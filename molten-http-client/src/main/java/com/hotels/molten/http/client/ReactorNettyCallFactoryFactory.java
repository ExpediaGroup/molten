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
import java.util.List;

import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.LoggerFactory;
import reactor.netty.channel.BootstrapHandlers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientMetricsRecorder;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

import com.hotels.molten.http.client.metrics.DelegatingHttpClientMetricsReporter;
import com.hotels.molten.http.client.metrics.LoggingHttpClientMetricsRecorder;
import com.hotels.molten.http.client.metrics.MicrometerHttpClientMetricsRecorder;
import com.hotels.molten.http.client.retrofit.ReactorNettyCallFactory;

/**
 * Reactor netty backed call factory factory.
 */
class ReactorNettyCallFactoryFactory implements CallFactoryFactory {
    private static final LoopResources LOOP_RESOURCES = LoopResources.create("molten-http");

    @Override
    public okhttp3.Call.Factory createCallFactory(RetrofitServiceClientConfiguration<?> configuration, String clientId) {
        var connectionProvider = createConnectionProvider(configuration, clientId);
        var httpClient = HttpClient.create(connectionProvider)
            .tcpConfiguration(tcpClient ->
                tcpClient
                    .runOn(LOOP_RESOURCES) // should be singleton shared among clients
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) configuration.getConnectionSettings().getTimeout().toMillis()) // connection timeout in ms
                    .option(ChannelOption.TCP_NODELAY, true) // TODO: should this be configurable?
                    .metrics(false) // disabled as too verbose for hierarchical reporter
            )
            .followRedirect(false)
            .compress(true)
            .disableRetry(!configuration.getConnectionSettings().isRetryOnConnectionFailure())
            .headers(headers -> {
                configuration.getRequestTracking().getClientIdSupplier().get().ifPresent(header -> headers.add(header.getName(), header.getValue()));
                configuration.getRequestTracking().getRequestIdSupplier().get().ifPresent(header -> headers.add(header.getName(), header.getValue()));
                configuration.getRequestTracking().getSessionIdSupplier().get().ifPresent(header -> headers.add(header.getName(), header.getValue()));
            });

        var sslContextConfiguration = configuration.getSslContextConfiguration();
        if (sslContextConfiguration != null) {
            httpClient = httpClient.secure(spec -> {
                var sslContextBuilder = SslContextBuilder.forClient();
                sslContextConfiguration.getProtocol().ifPresent(sslContextBuilder::protocols);
                sslContextConfiguration.getTrustManager().ifPresent(sslContextBuilder::trustManager);
                sslContextConfiguration.getKeyManager().ifPresent(sslContextBuilder::keyManager);
                spec.sslContext(sslContextBuilder);
            });
        }
        httpClient = addHttpTracing(httpClient, configuration, clientId);
        var finalClient = httpClient;
        return new ReactorNettyCallFactory(() -> finalClient, configuration.getHttpTracing());
    }

    private ConnectionProvider createConnectionProvider(RetrofitServiceClientConfiguration<?> configuration, String clientId) {
        var concurrency = configuration.getConcurrency();
        return ConnectionProvider.builder(clientId)
            .maxConnections(concurrency * 2)
            .pendingAcquireMaxCount(concurrency * 2) // this will limit max inflight requests as well
            .pendingAcquireTimeout(configuration.getConnectionSettings().getTimeout())
            .maxIdleTime(configuration.getConnectionSettings().getKeepAliveIdle())
            .maxLifeTime(configuration.getConnectionSettings().getMaxLife())
            .fifo()
            // See reactor.netty.resources.PooledConnectionProviderMetrics
            // It would register metrics per address pool under reactor.netty.connection.provider.[poolname] with lots of tags.
            // Could be filtered by Metrics.globalRegistry.config().meterFilter(MeterFilter.maximumAllowableTags(CONNECTION_PROVIDER_PREFIX, 100, filter));
            // We could consider enabling this when dimensional metrics are enabled but graphite based ones are not.
            //.metrics(MetricsSupport.isDimensionalMetricsEnabled() && !MetricsSupport.isGraphiteIdMetricsLabelEnabled())
            // But we keep it disabled for now.
            .metrics(false)
            //TODO: handle additional problematic netty metrics i.e. reactor.netty.bytebuf and reactor.netty.pooled
            .build();
    }

    private HttpClient addHttpTracing(HttpClient httpClient, RetrofitServiceClientConfiguration<?> configuration, String clientId) {
        HttpClient client = httpClient;
        List<HttpClientMetricsRecorder> recorders = new ArrayList<>();
        if (configuration.isReportingHttpEventsEnabled() && configuration.getMeterRegistry() != null) {
            recorders.add(new MicrometerHttpClientMetricsRecorder(configuration.getMeterRegistry(), clientId));
        }
        if (configuration.isLoggingHttpEventsEnabled()) {
            recorders.add(new LoggingHttpClientMetricsRecorder(LoggerFactory.getLogger(configuration.getApi())));
        }
        if (configuration.isLogDetailedHttpEventsEnabled()) {
            client = client
                .tcpConfiguration(tcpClient -> tcpClient.bootstrap(b -> BootstrapHandlers.updateLogSupport(b, new LoggingHandler(configuration.getApi(), LogLevel.INFO))));
        }
        if (!recorders.isEmpty()) {
            client = client.metrics(true, () -> new DelegatingHttpClientMetricsReporter(recorders));
        }
        return client;
    }
}
