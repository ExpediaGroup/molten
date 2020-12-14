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

import java.net.SocketAddress;
import java.time.Duration;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import reactor.netty.http.client.HttpClientMetricsRecorder;

@RequiredArgsConstructor
public class LoggingHttpClientMetricsRecorder implements HttpClientMetricsRecorder {
    @NonNull
    private final Logger logger;

    @Override
    public void recordDataReceivedTime(SocketAddress remoteAddress, String uri, String method, String status, Duration time) {
        logger.info("http - dataReceivedTime remoteAddress={} uri={} method={} status={} durationMs={}", remoteAddress, uri, method, status, time.toMillis());
    }

    @Override
    public void recordDataSentTime(SocketAddress remoteAddress, String uri, String method, Duration time) {
        logger.info("http - dataSentTime remoteAddress={} uri={} method={} durationMs={}", remoteAddress, uri, method, time.toMillis());
    }

    @Override
    public void recordResponseTime(SocketAddress remoteAddress, String uri, String method, String status, Duration time) {
        logger.info("http - responseTime remoteAddress={} uri={} method={} status={} durationMs={}", remoteAddress, uri, method, status, time.toMillis());
    }

    @Override
    public void recordDataReceived(SocketAddress remoteAddress, String uri, long bytes) {
        logger.info("http - dataReceived remoteAddress={} uri={} bytes={}", remoteAddress, uri, bytes);
    }

    @Override
    public void recordDataSent(SocketAddress remoteAddress, String uri, long bytes) {
        logger.info("http - dataSent remoteAddress={} uri={} bytes={}", remoteAddress, uri, bytes);
    }

    @Override
    public void incrementErrorsCount(SocketAddress remoteAddress, String uri) {
        logger.info("http - incrementErrorsCount remoteAddress={} uri={}", remoteAddress, uri);
    }

    @Override
    public void recordDataReceived(SocketAddress remoteAddress, long bytes) {
        logger.info("channel - dataReceived remoteAddress={} bytes={}", remoteAddress, bytes);
    }

    @Override
    public void recordDataSent(SocketAddress remoteAddress, long bytes) {
        logger.info("channel - dataSent remoteAddress={} bytes={}", remoteAddress, bytes);
    }

    @Override
    public void incrementErrorsCount(SocketAddress remoteAddress) {
        logger.info("channel - incrementErrorsCount remoteAddress={}", remoteAddress);
    }

    @Override
    public void recordTlsHandshakeTime(SocketAddress remoteAddress, Duration time, String status) {
        logger.info("channel - tlsHandshakeTime remoteAddress={} status={} durationMs={}", remoteAddress, status, time.toMillis());
    }

    @Override
    public void recordConnectTime(SocketAddress remoteAddress, Duration time, String status) {
        logger.info("channel - connectTime remoteAddress={} status={} durationMs={}", remoteAddress, status, time.toMillis());
    }

    @Override
    public void recordResolveAddressTime(SocketAddress remoteAddress, Duration time, String status) {
        logger.info("channel - resolveAddressTime remoteAddress={} status={} durationMs={}", remoteAddress, status, time.toMillis());
    }
}
