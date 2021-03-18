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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit2.adapter.reactor.ReactorCallAdapterFactory;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.LoggingEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Unit test for {@link OkHttpClient}.
 */
public class OkHttpTest {
    private static final Logger LOG = LoggerFactory.getLogger("TEST");

    private static final StubJsonServiceServer SERVER = new StubJsonServiceServer();
    private static final String BASE_URL = "http://localhost:" + SERVER.getPort() + "/";

    @BeforeClass
    public void initServer() {
        SERVER.start(true, false);
    }

    @Test
    @Ignore
    public void skeletonToTestOkHttpDirectly() throws InterruptedException {
        int concurrency = 1;
        int connectionTimeoutMs = 1000;
        int readTimeoutMs = 3000;
        boolean retryConnectionFailure = true;

        ConnectionPool connectionPool = connectionPoolFor(concurrency);
        Dispatcher dispatcher = dispatcherFor(concurrency);
        OkHttpClient.Builder clientBuilder = new OkHttpClient().newBuilder()
            .connectTimeout(connectionTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .connectionPool(connectionPool)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(retryConnectionFailure)
            .dispatcher(dispatcher)
            .addNetworkInterceptor(requestLogger())
            .eventListenerFactory(new LoggingEventListener.Factory(LoggerFactory.getLogger("HTTP.events")::trace));

        Retrofit.Builder retrofitBuilder = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(clientBuilder.build())
            .addCallAdapterFactory(ReactorCallAdapterFactory.create())
            .addConverterFactory(JacksonConverterFactory.create(new ObjectMapper()));

        Retrofit retrofit = retrofitBuilder.build();
        ServiceEndpoint client = retrofit.create(ServiceEndpoint.class);

        LOG.info("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< init {}", Thread.currentThread().getName());
        client.getDelayed(100)
            .doOnSubscribe(s -> LOG.info("subscribing initial successful"))
            .doFinally(s -> LOG.info("finally init {}", s))
            .block();
        Thread.sleep(100);
        LOG.info("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< test");
        Disposable subscription0 = client.getDelayed(1000)
            .doOnSubscribe(s -> LOG.info("subscribing 0"))
            .subscribeOn(Schedulers.parallel())
            .timeout(Duration.ofMillis(320))
            .doFinally(s -> LOG.info("finally 0 {}", s))
            .subscribe(r -> LOG.info("result 0 {}", r), e -> LOG.error("error 0", e), () -> LOG.info("completed 0"));
        //runDelayed(client, 1);
        //Thread.sleep(50);
        //subscription0.dispose();
        //Thread.sleep(25);
        //runDelayed(client, 2);
        Thread.sleep(2000);
        LOG.info("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< post");
        client.getDelayed(100)
            .doOnSubscribe(s -> LOG.info("subscribing post"))
            .subscribeOn(Schedulers.parallel())
            .doFinally(s -> LOG.info("finally post {}", s))
            .subscribe(r -> LOG.info("result post {}", r), e -> LOG.error("error post", e), () -> LOG.info("completed post"));
        Thread.sleep(50000);
    }

    private void runDelayed(ServiceEndpoint client, int id) {
        client.getDelayed(1000 + id)
            .doOnSubscribe(s -> LOG.info("subscribing {}", id))
            .subscribeOn(Schedulers.parallel())
            .doFinally(s -> LOG.info("finally {} {}", id, s))
            .subscribe(r -> LOG.info("result {} {}", id, r), e -> LOG.error("error {}", id, e), () -> LOG.info("completed {}", id));
    }

    private ConnectionPool connectionPoolFor(int concurrency) {
        return new ConnectionPool(concurrency, 15 * 60, TimeUnit.SECONDS);
    }

    private Dispatcher dispatcherFor(int concurrency) {
        ExecutorService executorService = new MDCCopyingThreadPoolExecutor();
        Dispatcher dispatcher = new Dispatcher(executorService);
        dispatcher.setMaxRequests(concurrency * 2);
        dispatcher.setMaxRequestsPerHost(concurrency * 2);
        return dispatcher;
    }

    private HttpLoggingInterceptor requestLogger() {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(LoggerFactory.getLogger("HTTP.log")::trace);
        if (LOG.isTraceEnabled()) {
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        }
        return interceptor;
    }

    private RetrofitServiceClientBuilder<ServiceEndpoint> defaultClientBuilder() {
        return RetrofitServiceClientBuilder.createOver(ServiceEndpoint.class, BASE_URL)
            .groupId("grp1");
    }
}
