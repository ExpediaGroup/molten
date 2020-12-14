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
import java.util.List;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.netty.http.client.HttpClientMetricsRecorder;

@RequiredArgsConstructor
public class DelegatingHttpClientMetricsReporter implements HttpClientMetricsRecorder {
    @NonNull
    private final List<HttpClientMetricsRecorder> recorders;

    @Override
    public void recordDataReceivedTime(SocketAddress remoteAddress, String uri, String method, String status, Duration time) {
        recorders.forEach(recorder -> recorder.recordDataReceivedTime(remoteAddress, uri, method, status, time));
    }

    @Override
    public void recordDataSentTime(SocketAddress remoteAddress, String uri, String method, Duration time) {
        recorders.forEach(recorder -> recorder.recordDataSentTime(remoteAddress, uri, method, time));
    }

    @Override
    public void recordResponseTime(SocketAddress remoteAddress, String uri, String method, String status, Duration time) {
        recorders.forEach(recorder -> recorder.recordResponseTime(remoteAddress, uri, method, status, time));
    }

    @Override
    public void recordDataReceived(SocketAddress remoteAddress, String uri, long bytes) {
        recorders.forEach(recorder -> recorder.recordDataReceived(remoteAddress, uri, bytes));
    }

    @Override
    public void recordDataSent(SocketAddress remoteAddress, String uri, long bytes) {
        recorders.forEach(recorder -> recorder.recordDataSent(remoteAddress, uri, bytes));
    }

    @Override
    public void incrementErrorsCount(SocketAddress remoteAddress, String uri) {
        recorders.forEach(recorder -> recorder.incrementErrorsCount(remoteAddress, uri));
    }

    @Override
    public void recordDataReceived(SocketAddress remoteAddress, long bytes) {
        recorders.forEach(recorder -> recorder.recordDataReceived(remoteAddress, bytes));
    }

    @Override
    public void recordDataSent(SocketAddress remoteAddress, long bytes) {
        recorders.forEach(recorder -> recorder.recordDataSent(remoteAddress, bytes));
    }

    @Override
    public void incrementErrorsCount(SocketAddress remoteAddress) {
        recorders.forEach(recorder -> recorder.incrementErrorsCount(remoteAddress));
    }

    @Override
    public void recordTlsHandshakeTime(SocketAddress remoteAddress, Duration time, String status) {
        recorders.forEach(recorder -> recorder.recordTlsHandshakeTime(remoteAddress, time, status));
    }

    @Override
    public void recordConnectTime(SocketAddress remoteAddress, Duration time, String status) {
        recorders.forEach(recorder -> recorder.recordConnectTime(remoteAddress, time, status));
    }

    @Override
    public void recordResolveAddressTime(SocketAddress remoteAddress, Duration time, String status) {
        recorders.forEach(recorder -> recorder.recordResolveAddressTime(remoteAddress, time, status));
    }
}
