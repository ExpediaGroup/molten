/*
 * Copyright (c) 2020 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hotels.molten.http.client;

import static org.testng.Assert.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import brave.http.HttpTracing;
import brave.vertx.web.VertxWebTracing;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.common.primitives.Ints;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * A HTTP server with JSON API to test different kind of requests.
 */
public class StubJsonServiceServer {
    public static final String SUCCESS = "success";
    private static final Logger LOG = LoggerFactory.getLogger(StubJsonServiceServer.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final XmlMapper XML = XmlMapper.builder().addModule(new JacksonXmlModule()).defaultUseWrapper(false).build();
    private int port;
    private volatile HttpServer httpServer;

    static {
        System.setProperty(io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME, SLF4JLogDelegateFactory.class.getName());
        io.vertx.core.logging.LoggerFactory.initialise();
    }

    public StubJsonServiceServer() {
        port = getFreePort();
    }

    public static void main(String... args) {
        new StubJsonServiceServer().start(true, Optional.ofNullable(args[0]).map(Boolean::valueOf).orElse(false));
    }

    public void start(boolean compressionSupported, boolean isTlsRequired) {
        LOG.info("Using vertx cache dir: {}", Optional.ofNullable(System.getProperty("vertx.cacheDirBase")).orElse("default"));
        shutDown();
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        if (HttpTracing.current() != null) {
            var routingContextHandler = VertxWebTracing.create(HttpTracing.current()).routingContextHandler();
            router.route().order(-2).handler(routingContextHandler).failureHandler(routingContextHandler);
        }
        router.route().handler(LoggerHandler.create(LoggerFormat.DEFAULT));
        router.route().handler(BodyHandler.create().setBodyLimit(1024 * 1024));
        // return just a Response with the id parameter as text either as json or xml via separate routes
        router.route("/get/:id").handler(rc -> rc.response().end(toJson(response(rc.pathParam("id"), 1))));
        router.route("/getquery").handler(rc -> rc.response().end(toJson(response(rc.queryParam("id").stream().findFirst().orElse("na"), 1))));
        router.route("/get/xml/:id").handler(rc -> rc.response().end(toXml(response(rc.pathParam("id"), 1))));
        // post returns request body data as response
        router.route(HttpMethod.POST, "/post").handler(rc -> {
            JsonObject bodyAsJson = rc.getBodyAsJson();
            rc.response().end(toJson(response(bodyAsJson.getString("text"), bodyAsJson.getInteger("number"))));
        });
        // does not return anything
        router.route(HttpMethod.DELETE, "/delete/:id").handler(rc -> rc.response().end());
        // may return or not return something
        router.route("/find/:id").handler(rc -> {
            if (rc.pathParam("id").equals("empty")) {
                rc.response().setStatusCode(204).end();
            } else {
                rc.response().end(toJson(response(rc.pathParam("id"), 1)));
            }
        });
        // fails the request with the requested code
        router.route("/status/:code").handler(rc -> {
            int code = Optional.ofNullable(rc.pathParam("code")).map(Ints::tryParse).orElse(200);
            if (code < 400) {
                rc.response().setStatusCode(code).end(toJson(SUCCESS));
            } else {
                rc.response().setStatusCode(code).end(toJson("error"));
            }
        });
        //Check http protocols, if not match with the request respond with 500
        router.route("/checkProtocol/:http").handler(rc -> {
            var version = rc.request().version().name();
            if (version.equals(rc.pathParam("http"))) {
                rc.response().setStatusCode(200).end(toJson(SUCCESS));
            } else {
                rc.response().setStatusCode(500).end(toJson("error"));
            }
        });
        // responds with success delayed with ms milliseconds
        router.route("/delay/:ms").handler(rc -> {
            int timeout = Optional.ofNullable(rc.pathParam("ms")).map(Ints::tryParse).orElse(0);
            Mono.just(timeout).delayElement(Duration.ofMillis(timeout)).subscribe(i -> {
                if (rc.response().closed()) {
                    LOG.info("Connection is already closed.");
                } else {
                    rc.response().end(toJson(SUCCESS));
                }
            });
        });
        // responds with 503 delayed with ms milliseconds
        router.route("/delaywithfailure/:ms").handler(rc -> {
            int timeout = Optional.ofNullable(rc.pathParam("ms")).map(Ints::tryParse).orElse(0);
            Mono.just(timeout).delayElement(Duration.ofMillis(timeout)).subscribe(i -> rc.fail(503));
        });
        final AtomicInteger counter = new AtomicInteger();
        // resets the server counter
        router.route("/resetcounter").handler(rc -> {
            counter.set(0);
            rc.response().end(toJson(SUCCESS));
        });
        // returns with success for every nth (period) request (based on counter), otherwise fails with code
        router.route("/maybe/:period/:code").handler(rc -> {
            int period = Optional.ofNullable(rc.pathParam("period")).map(Ints::tryParse).orElse(1);
            int code = Optional.ofNullable(rc.pathParam("code")).map(Ints::tryParse).orElse(503);
            if (counter.incrementAndGet() % period == 0) {
                rc.response().end(toJson(SUCCESS));
            } else {
                rc.fail(code);
            }
        });
        addHttpHeaderEndpoints(router);

        try {
            CountDownLatch latch = new CountDownLatch(1);
            httpServer = vertx.createHttpServer(httpServerOptions(compressionSupported, isTlsRequired))
                    .requestHandler(router)
                    .listen(port, i -> latch.countDown());
            latch.await();
            LOG.info("Stub server is running on port {}", port);
        } catch (InterruptedException e) {
            fail("Server start up has been interrupted.");
        }
    }

    private void addHttpHeaderEndpoints(Router router) {
        // returns the request header sent by its name in body
        router.route("/header/:name")
            .handler(rc -> {
                var headersJoined = Optional.ofNullable(rc.request().headers().getAll(rc.pathParam("name")))
                    .map(headers -> String.join(",", headers))
                    .orElse("N/A");
                rc.response().end(toJson(headersJoined));
            });

        // supports Accept and Content-Type headers for application/json and application/xml
        router.route(HttpMethod.POST, "/contenttype")
            .handler(rc -> {
                String contentType = Optional.ofNullable(rc.request().getHeader(HttpHeaderNames.CONTENT_TYPE)).orElse("n/a");
                String accept = Optional.ofNullable(rc.request().getHeader(HttpHeaderNames.ACCEPT)).orElse("n/a");
                if ("*/*".equals(accept)) { //FIXME: currently we are not handling accept based on client configuration in RetrofitServiceClientBuilder
                    accept = contentType;
                }
                Request request;
                try {
                    if (contentType.startsWith("application/json")) {
                        request = new ObjectMapper().readValue(rc.getBodyAsString(), Request.class);
                    } else if (contentType.startsWith("application/xml")) {
                        request = new XmlMapper().readValue(rc.getBodyAsString(), Request.class);
                    } else {
                        throw new IllegalArgumentException("Unsupported content type " + contentType);
                    }
                    var response = response(request.getText(), request.getNumber());
                    String responseBody;
                    if (accept.startsWith("application/json")) {
                        responseBody = toJson(response);
                    } else if (accept.startsWith("application/xml")) {
                        responseBody = toXml(response);
                    } else {
                        throw new IllegalArgumentException("Unsupported accept type " + accept);
                    }
                    rc.response().end(responseBody);
                } catch (Exception e) {
                    LOG.error("Error processing contenttype request", e);
                    rc.fail(e);
                }
            });

        // returns the ping request header sent as pong response header
        router.route("/pingheader")
            .handler(rc -> {
                rc.response().headers().add("pong", Optional.ofNullable(rc.request().getHeader("ping")).orElse("N/A"));
                rc.response().end(toJson(response("value", 42)));
            });
    }

    private HttpServerOptions httpServerOptions(boolean compressionSupported, boolean isTlsRequired) {
        HttpServerOptions httpServerOptions = new HttpServerOptions()
                .setCompressionSupported(compressionSupported);
        if (isTlsRequired) {
            httpServerOptions.setSsl(true)
                .removeEnabledSecureTransportProtocol("TLSv1")
                .removeEnabledSecureTransportProtocol("TLSv1.1")
                .setKeyStoreOptions(new JksOptions().setPath("certificate/testkeystore.jks").setPassword("password"))
                .setJdkSslEngineOptions(new JdkSSLEngineOptions())
                .addEnabledCipherSuite("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
                .addEnabledCipherSuite("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
                .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA");
        }
        return httpServerOptions;
    }

    public void shutDown() {
        if (httpServer != null) {
            try {
                CountDownLatch latch = new CountDownLatch(1);
                httpServer.close(i -> latch.countDown());
                latch.await();
                httpServer = null;
                LOG.info("Stub server has been stopped");
            } catch (InterruptedException e) {
                fail("Server shut down has been interrupted.");
            }
        }
    }

    public int getPort() {
        return port;
    }

    private int getFreePort() {
        try {
            return new ServerSocket(0).getLocalPort();
        } catch (IOException e) {
            fail("Couldn't reserve port.");
            throw new RuntimeException(e);
        }
    }

    private static String toJson(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String toXml(Object obj) {
        try {
            return XML.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Response response(String text, int number) {
        return new Response(text, number);
    }
}
