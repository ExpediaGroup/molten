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

import java.time.Duration;
import java.util.Optional;
import java.util.Random;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit2.adapter.reactor.ReactorCallAdapterFactory;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;
import reactor.test.StepVerifier;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import com.hotels.molten.http.client.retrofit.ReactorNettyCallFactory;
import com.hotels.molten.trace.test.TracingTestSupport;

/**
 * Unit test for reactor-netty.
 * WebClient: org.springframework.http.client.reactive.ReactorClientHttpConnector
 * Tracing: org.springframework.cloud.sleuth.instrument.web.client.TraceWebClientBeanPostProcessor
 * Metrics: reactor.netty.http.client.HttpClient#metrics(boolean, reactor.netty.http.client.HttpClientMetricsRecorder)
 * reactor.netty.channel.MicrometerChannelMetricsRecorder
 */
public class ReactorNettyHttpClientTest {
    private static final Logger LOG = LoggerFactory.getLogger("TEST");

    private static final StubJsonServiceServer SERVER = new StubJsonServiceServer();
    private static final String BASE_URL = "http://localhost:" + SERVER.getPort();

    @BeforeClass
    public void initServer() {
        SERVER.start(true, false);
    }

    @Test
    public void shouldWork() throws InterruptedException {
        var connectionProvider = ConnectionProvider.builder("fixed")
            .name("my-connection-pool")
            .maxConnections(1)
            .pendingAcquireMaxCount(2)
            .pendingAcquireTimeout(Duration.ofMillis(30000))
            .maxIdleTime(Duration.ofSeconds(15))
            .maxLifeTime(Duration.ofMinutes(10))
            .fifo()
            // see reactor.netty.resources.PooledConnectionProviderMetrics
            // will register metrics per address pool under reactor.netty.connection.provider.[poolname] with lots of tags. needs to check if this is fine
            .metrics(true)
            .build();
        HttpClient.create(connectionProvider)
            .protocol(HttpProtocol.H2C)
            //.secure(spec -> spec.sslContext(SslContextBuilder.forClient()))
            .runOn(LoopResources.create("molten-http")) // should be singleton shared among clients
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // connection timeout
            .option(ChannelOption.TCP_NODELAY, true)
            .followRedirect(false)
            .compress(true)
            .baseUrl(BASE_URL)
            .get()
            .uri("/delay/100")
            .responseContent()
            .asString()
            .doOnSubscribe(s -> LOG.info("subscribing 0"))
            .timeout(Duration.ofMillis(1000))
            .doFinally(s -> LOG.info("finally 0 {}", s))
            .as(StepVerifier::create)
            .expectNext("\"success\"")
            .verifyComplete();
    }

    @Test
    public void shouldWorkWithRetrofit() throws InterruptedException {
        MeterRegistry meterRegistry = MeterRegistrySupport.simpleRegistry();
        Metrics.addRegistry(meterRegistry);
        Metrics.globalRegistry.config()
            .meterFilter(new MeterFilter() {
                private final MeterFilter tagFilter = MeterFilter.ignoreTags("id", "remote.address");

                @Override
                public Meter.Id map(Meter.Id meterId) {
                    return Optional.ofNullable(meterId)
                        .filter(id -> id.getName().startsWith(reactor.netty.Metrics.CONNECTION_PROVIDER_PREFIX))
                        .map(tagFilter::map)
                        .orElse(meterId);
                }
            });
        Metrics.globalRegistry.config()
            .meterFilter(new MeterFilter() {
                private final MeterFilter tagFilter = MeterFilter.ignoreTags("remote.address", "method", "status", "uri");

                @Override
                public Meter.Id map(Meter.Id meterId) {
                    return Optional.ofNullable(meterId)
                        .filter(id -> id.getName().startsWith(reactor.netty.Metrics.HTTP_CLIENT_PREFIX))
                        .map(tagFilter::map)
                        .orElse(meterId);
                }
            });
        Metrics.globalRegistry.config()
            .meterFilter(new MeterFilter() {
                private final MeterFilter tagFilter = MeterFilter.ignoreTags("uri", "remote.address", "status", "uri");

                @Override
                public Meter.Id map(Meter.Id meterId) {
                    return Optional.ofNullable(meterId)
                        .filter(id -> id.getName().startsWith(reactor.netty.Metrics.TCP_CLIENT_PREFIX))
                        .map(tagFilter::map)
                        .orElse(meterId);
                }
            });

        var connectionProvider = ConnectionProvider.builder("fixed")
            .name("my-connection-pool")
            .maxConnections(20)
            .pendingAcquireMaxCount(20) // this will limit max inflight requests
            .pendingAcquireTimeout(Duration.ofMillis(30000))
            .maxIdleTime(Duration.ofSeconds(15))
            .maxLifeTime(Duration.ofMinutes(10))
            .fifo()
            // see reactor.netty.resources.PooledConnectionProviderMetrics
            // will register metrics per address pool under reactor.netty.connection.provider.[poolname] with lots of tags.
            // Metrics.globalRegistry.config().meterFilter(MeterFilter.maximumAllowableTags(CONNECTION_PROVIDER_PREFIX, 100, filter));
            .metrics(true)
            .build();
        var httpClient = HttpClient.create(connectionProvider)
            .protocol(HttpProtocol.HTTP11)
            //.secure(spec -> spec.sslContext(SslContextBuilder.forClient()))
            .runOn(LoopResources.create("molten-http"))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .option(ChannelOption.TCP_NODELAY, true)
            .followRedirect(false)
            .compress(true)
            .metrics(true, u -> u)
            .baseUrl(BASE_URL);
        var retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .callFactory(new ReactorNettyCallFactory(() -> httpClient, TracingTestSupport.httpTracing()))
            .addCallAdapterFactory(ForbiddenResultReactorCallAdapterFactoryDecorator.decorate(ReactorCallAdapterFactory.createAsync()))
            .addConverterFactory(JacksonConverterFactory.create(new ObjectMapper()))
            .build();

        ServiceEndpoint serviceClient = retrofit.create(ServiceEndpoint.class);

        serviceClient.postData(new Request("mirror me!", 1234))
            .doOnSubscribe(s -> LOG.info("subscribing"))
            .timeout(Duration.ofMillis(1000))
            .doFinally(s -> LOG.info("finally {}", s))
            .as(StepVerifier::create)
            .expectNext(new Response("mirror me!", 1234))
            .verifyComplete();

        Flux.interval(Duration.ZERO, Duration.ofMillis(100))
            .take(100)
            .flatMap(i -> serviceClient.getDelayed(new Random().nextInt(400) + 100)
                .doOnSubscribe(s -> LOG.info("subscribing"))
                .timeout(Duration.ofMillis(1000))
                .doFinally(s -> LOG.info("finally {}", s))
            )
            .as(StepVerifier::create)
            .expectNextCount(100)
            .verifyComplete();
    }
}
