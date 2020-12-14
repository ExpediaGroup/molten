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

package com.hotels.molten.http.client.metrics;

import static com.hotels.molten.core.metrics.MetricsSupport.name;

import java.net.SocketAddress;
import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.netty.http.client.HttpClientMetricsRecorder;

import com.hotels.molten.core.metrics.MetricId;

@RequiredArgsConstructor
public final class MicrometerHttpClientMetricsRecorder implements HttpClientMetricsRecorder {
    @NonNull
    private final MeterRegistry meterRegistry;
    @NonNull
    private final String clientId;

    @Override
    public void recordDataReceivedTime(SocketAddress remoteAddress, String uri, String method, String status, Duration time) {
        registerEvent("data-received", time);
    }

    @Override
    public void recordDataSentTime(SocketAddress remoteAddress, String uri, String method, Duration time) {
        registerEvent("data-sent", time);
    }

    @Override
    public void recordResponseTime(SocketAddress remoteAddress, String uri, String method, String status, Duration time) {
        registerEvent("response", time);
    }

    @Override
    public void recordDataReceived(SocketAddress remoteAddress, String uri, long bytes) {

    }

    @Override
    public void recordDataSent(SocketAddress remoteAddress, String uri, long bytes) {

    }

    @Override
    public void incrementErrorsCount(SocketAddress remoteAddress, String uri) {

    }

    @Override
    public void recordDataReceived(SocketAddress remoteAddress, long bytes) {

    }

    @Override
    public void recordDataSent(SocketAddress remoteAddress, long bytes) {

    }

    @Override
    public void incrementErrorsCount(SocketAddress remoteAddress) {

    }

    @Override
    public void recordTlsHandshakeTime(SocketAddress remoteAddress, Duration time, String status) {
        registerEvent("tls-handshake", time);
    }

    @Override
    public void recordConnectTime(SocketAddress remoteAddress, Duration time, String status) {
        registerEvent("connect", time);
    }

    @Override
    public void recordResolveAddressTime(SocketAddress remoteAddress, Duration time, String status) {
        registerEvent("resolve-address", time);
    }

    private void registerEvent(String event, Duration time) {
        MetricId.builder()
            .name("http_client_request_trace_duration")
            .hierarchicalName(hierarchicalName(event))
            .tag(Tag.of("client", clientId))
            .tag(Tag.of("event", event))
            .build()
            .toTimer()
            .register(meterRegistry)
            .record(time);
    }

    private String hierarchicalName(String event) {
        return name("client", clientId, "http-trace", "duration", event);
    }
}
