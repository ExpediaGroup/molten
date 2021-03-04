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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import brave.okhttp3.TracingInterceptor;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.LoggingEventListener;
import org.slf4j.LoggerFactory;

import com.hotels.molten.http.client.listener.CollectingDelegatingEventsListener;
import com.hotels.molten.http.client.listener.DelegatingEventListener;
import com.hotels.molten.http.client.listener.DurationMetricsReporterHandler;
import com.hotels.molten.http.client.listener.HttpCallMetricsHandler;
import com.hotels.molten.http.client.listener.HttpMetricsReporterHandler;
import com.hotels.molten.http.client.listener.LoggingHttpCallMetricsHandler;
import com.hotels.molten.http.client.metrics.ConnectionPoolInstrumenter;
import com.hotels.molten.http.client.metrics.DispatcherInstrumenter;

/**
 * OkHTTP backed call factory factory.
 */
class OkHttpCallFactoryFactory implements CallFactoryFactory {
    @Override
    public okhttp3.Call.Factory createCallFactory(RetrofitServiceClientConfiguration<?> configuration, String clientId) {
        ConnectionPool connectionPool = connectionPoolFor(configuration);
        Dispatcher dispatcher = dispatcherFor(configuration);
        OkHttpClient.Builder clientBuilder = new OkHttpClient().newBuilder()
            .connectTimeout(configuration.getConnectionSettings().getTimeout())
            // Please note that read and write is the timeout on the socket to receive the next packet.
            .readTimeout(configuration.getExpectedLoad().getPeakResponseTime())
            .writeTimeout(configuration.getExpectedLoad().getPeakResponseTime())
            // callTimeout is intentionally not added as we have a timeout on top of this already and would add unnecessary threading
            .connectionPool(connectionPool)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(configuration.getConnectionSettings().isRetryOnConnectionFailure())
            .dispatcher(dispatcher)
            .addInterceptor(new RequestTrackingInterceptor(configuration.getRequestTracking()))
            .addNetworkInterceptor(requestLogger(configuration));
        //TODO make socket factory configurable (e.g. TCP_NO_DELAY) using clientBuilder.socketFactory(). also propagate configuration to ssl socket factory
        var sslContextConfiguration = configuration.getSslContextConfiguration();
        if (sslContextConfiguration != null) {
            sslContextConfiguration.getTrustManager().ifPresent(tm -> {
                try {
                    SSLContext sslContext = SSLContext.getInstance(sslContextConfiguration.getProtocol().orElse("TLS"));
                    sslContext.init(
                        sslContextConfiguration.getKeyManager().map(km -> new KeyManager[] {km}).orElse(null),
                        new TrustManager[] {tm},
                        null);
                    clientBuilder.sslSocketFactory(sslContext.getSocketFactory(), tm);
                } catch (Exception e) {
                    throw new IllegalStateException("Couldn't initialize SSL context", e);
                }
            });
        }
        if (configuration.getProtocol() != null) {
            clientBuilder.protocols(Collections.singletonList(getProtocol(configuration.getProtocol())));
        }
        if (configuration.getHttpTracing() != null) {
            clientBuilder.addNetworkInterceptor(TracingInterceptor.create(configuration.getHttpTracing()));
        }
        if (configuration.getMeterRegistry() != null) {
            new ConnectionPoolInstrumenter(configuration.getMeterRegistry(), clientId).instrument(connectionPool);
            new DispatcherInstrumenter(configuration.getMeterRegistry(), clientId).instrument(dispatcher);
        }
        addEventTracing(clientBuilder, configuration, clientId);
        return clientBuilder.build();
    }

    private ConnectionPool connectionPoolFor(RetrofitServiceClientConfiguration<?> configuration) {
        return new ConnectionPool(configuration.getConcurrency(), configuration.getConnectionSettings().getKeepAliveIdle().toSeconds(), TimeUnit.SECONDS);
    }

    private Dispatcher dispatcherFor(RetrofitServiceClientConfiguration<?> configuration) {
        ExecutorService executorService = new MDCCopyingThreadPoolExecutor();
        if (configuration.getHttpTracing() != null) {
            executorService = configuration.getHttpTracing().tracing().currentTraceContext().executorService(executorService);
        }

        // or lift the number of max in-flight request, and replace the executor service in the dispatcher with one that has no queues and also doesn't block
        // also CB might make this redundant
        Dispatcher dispatcher = new Dispatcher(executorService);
        // The reason we permit double of concurrency is to mitigate the effect of queueing in case cancelled requests are still ramping down thus holding an execution slot.
        // In this case the call is queued and it is later executed messing up threadlocals.
        int concurrency = configuration.getConcurrency();
        dispatcher.setMaxRequests(concurrency * 2);
        dispatcher.setMaxRequestsPerHost(concurrency * 2);
        return dispatcher;
    }

    private HttpLoggingInterceptor requestLogger(RetrofitServiceClientConfiguration<?> configuration) {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(s -> LoggerFactory.getLogger(configuration.getApi()).trace(s));
        if (LoggerFactory.getLogger(configuration.getApi()).isTraceEnabled()) {
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        }
        return interceptor;
    }

    private void addEventTracing(OkHttpClient.Builder clientBuilder, RetrofitServiceClientConfiguration<?> configuration, String clientId) {
        List<HttpCallMetricsHandler> httpCallMetricsHandlers = new ArrayList<>();
        if (configuration.isReportingHttpEventsEnabled() && configuration.getMeterRegistry() != null) {
            httpCallMetricsHandlers.add(new DurationMetricsReporterHandler(configuration.getMeterRegistry(), clientId));
            httpCallMetricsHandlers.add(new HttpMetricsReporterHandler(configuration.getMeterRegistry(), clientId));
        }
        if (configuration.isLoggingHttpEventsEnabled()) {
            httpCallMetricsHandlers.add(new LoggingHttpCallMetricsHandler(LoggerFactory.getLogger(configuration.getApi())));
        }
        List<Function<Call, EventListener>> eventListenerFactories = new ArrayList<>();
        if (!httpCallMetricsHandlers.isEmpty()) {
            eventListenerFactories.add(call -> new CollectingDelegatingEventsListener(call.request().url(), httpCallMetricsHandlers));
        }
        if (configuration.isLogDetailedHttpEventsEnabled()) {
            eventListenerFactories.add(new LoggingEventListener.Factory(LoggerFactory.getLogger(configuration.getApi())::info)::create);
        }
        if (!eventListenerFactories.isEmpty()) {
            clientBuilder.eventListenerFactory(DelegatingEventListener.delegatedFactory(eventListenerFactories)::apply);
        }
    }

    private Protocol getProtocol(Protocols protocol) {
        Protocol result = null;
        if (protocol == Protocols.HTTP_2) {
            result = Protocol.HTTP_2;
        }
        if (protocol == Protocols.HTTP_1_1) {
            result = Protocol.HTTP_1_1;
        }
        if (protocol == Protocols.HTTP_2C) {
            result = Protocol.H2_PRIOR_KNOWLEDGE;
        }
        return result;
    }
}
