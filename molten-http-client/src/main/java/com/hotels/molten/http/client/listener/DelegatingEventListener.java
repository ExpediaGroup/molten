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

package com.hotels.molten.http.client.listener;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.HttpUrl;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@RequiredArgsConstructor
public class DelegatingEventListener extends EventListener {
    @NonNull
    private final List<EventListener> eventListeners;

    public static Function<Call, EventListener> delegatedFactory(List<Function<Call, EventListener>> eventListenerFactories) {
        return call -> {
            List<EventListener> eventListeners = eventListenerFactories.stream().map(elf -> elf.apply(call)).collect(Collectors.toList());
            return new DelegatingEventListener(eventListeners);
        };
    }

    @Override
    public void callEnd(@NotNull Call call) {
        eventListeners.forEach(el -> el.callEnd(call));
    }

    @Override
    public void callFailed(@NotNull Call call, @NotNull IOException ioe) {
        eventListeners.forEach(el -> el.callFailed(call, ioe));
    }

    @Override
    public void callStart(@NotNull Call call) {
        eventListeners.forEach(el -> el.callStart(call));
    }

    @Override
    public void canceled(@NotNull Call call) {
        eventListeners.forEach(el -> el.canceled(call));
    }

    @Override
    public void connectEnd(@NotNull Call call, @NotNull InetSocketAddress inetSocketAddress, @NotNull Proxy proxy, @Nullable Protocol protocol) {
        eventListeners.forEach(el -> el.connectEnd(call, inetSocketAddress, proxy, protocol));
    }

    @Override
    public void connectFailed(@NotNull Call call, @NotNull InetSocketAddress inetSocketAddress, @NotNull Proxy proxy, @Nullable Protocol protocol, @NotNull IOException ioe) {
        eventListeners.forEach(el -> el.connectFailed(call, inetSocketAddress, proxy, protocol, ioe));
    }

    @Override
    public void connectStart(@NotNull Call call, @NotNull InetSocketAddress inetSocketAddress, @NotNull Proxy proxy) {
        eventListeners.forEach(el -> el.connectStart(call, inetSocketAddress, proxy));
    }

    @Override
    public void connectionAcquired(@NotNull Call call, @NotNull Connection connection) {
        eventListeners.forEach(el -> el.connectionAcquired(call, connection));
    }

    @Override
    public void connectionReleased(@NotNull Call call, @NotNull Connection connection) {
        eventListeners.forEach(el -> el.connectionReleased(call, connection));
    }

    @Override
    public void dnsEnd(@NotNull Call call, @NotNull String domainName, @NotNull List<InetAddress> inetAddressList) {
        eventListeners.forEach(el -> el.dnsEnd(call, domainName, inetAddressList));
    }

    @Override
    public void dnsStart(@NotNull Call call, @NotNull String domainName) {
        eventListeners.forEach(el -> el.dnsStart(call, domainName));
    }

    @Override
    public void proxySelectEnd(@NotNull Call call, @NotNull HttpUrl url, @NotNull List<Proxy> proxies) {
        eventListeners.forEach(el -> el.proxySelectEnd(call, url, proxies));
    }

    @Override
    public void proxySelectStart(@NotNull Call call, @NotNull HttpUrl url) {
        eventListeners.forEach(el -> el.proxySelectStart(call, url));
    }

    @Override
    public void requestBodyEnd(@NotNull Call call, long byteCount) {
        eventListeners.forEach(el -> el.requestBodyEnd(call, byteCount));
    }

    @Override
    public void requestBodyStart(@NotNull Call call) {
        eventListeners.forEach(el -> el.requestBodyStart(call));
    }

    @Override
    public void requestFailed(@NotNull Call call, @NotNull IOException ioe) {
        eventListeners.forEach(el -> el.requestFailed(call, ioe));
    }

    @Override
    public void requestHeadersEnd(@NotNull Call call, @NotNull Request request) {
        eventListeners.forEach(el -> el.requestHeadersEnd(call, request));
    }

    @Override
    public void requestHeadersStart(@NotNull Call call) {
        eventListeners.forEach(el -> el.requestHeadersStart(call));
    }

    @Override
    public void responseBodyEnd(@NotNull Call call, long byteCount) {
        eventListeners.forEach(el -> el.responseBodyEnd(call, byteCount));
    }

    @Override
    public void responseBodyStart(@NotNull Call call) {
        eventListeners.forEach(el -> el.responseBodyStart(call));
    }

    @Override
    public void responseFailed(@NotNull Call call, @NotNull IOException ioe) {
        eventListeners.forEach(el -> el.responseFailed(call, ioe));
    }

    @Override
    public void responseHeadersEnd(@NotNull Call call, @NotNull Response response) {
        eventListeners.forEach(el -> el.responseHeadersEnd(call, response));
    }

    @Override
    public void responseHeadersStart(@NotNull Call call) {
        eventListeners.forEach(el -> el.responseHeadersStart(call));
    }

    @Override
    public void secureConnectEnd(@NotNull Call call, @Nullable Handshake handshake) {
        eventListeners.forEach(el -> el.secureConnectEnd(call, handshake));
    }

    @Override
    public void secureConnectStart(@NotNull Call call) {
        eventListeners.forEach(el -> el.secureConnectStart(call));
    }
}
