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
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.HttpUrl;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

/**
 * Event Listener for collecting call's metrics.
 * Delegates metrics handling to {@link HttpCallMetricsHandler} handlers at final call events: {@code callEnd}, {@code callFailed} or {@code cancelled}.
 * Only propagates metrics once even if multiple final call events has been registered.
 */
@RequiredArgsConstructor
@Slf4j
public class CollectingDelegatingEventsListener extends EventListener {
    private final Map<HttpEvent, Collection<Long>> httpCallMetrics = new ConcurrentHashMap<>();
    @NotNull
    private final HttpUrl httpUrl;
    @NotNull
    private final List<HttpCallMetricsHandler> httpCallMetricsHandlers;
    private volatile long startMs;
    @Setter(AccessLevel.PACKAGE)
    private Clock clock = Clock.systemDefaultZone();
    private volatile boolean finished;

    @Override
    public void callStart(Call call) {
        startMs = clock.millis();
        registerEvent(HttpEvent.CALL_START, 0L);
    }

    @Override
    public void proxySelectStart(Call call, HttpUrl url) {
        registerEvent(HttpEvent.PROXY_SELECT_START);
    }

    @Override
    public void proxySelectEnd(Call call, HttpUrl url, List<Proxy> proxies) {
        registerEvent(HttpEvent.PROXY_SELECT_END);
    }

    @Override
    public void dnsStart(Call call, String domainName) {
        registerEvent(HttpEvent.DNS_START);
    }

    @Override
    public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
        registerEvent(HttpEvent.DNS_END);
    }

    @Override
    public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
        registerEvent(HttpEvent.CONNECT_START);
    }

    @Override
    public void secureConnectStart(Call call) {
        registerEvent(HttpEvent.SECURE_CONNECT_START);
    }

    @Override
    public void secureConnectEnd(Call call, @Nullable Handshake handshake) {
        registerEvent(HttpEvent.SECURE_CONNECT_END);
    }

    @Override
    public void connectEnd(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, @Nullable Protocol protocol) {
        registerEvent(HttpEvent.CONNECT_END);
    }

    @Override
    public void connectFailed(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, @Nullable Protocol protocol, IOException ioe) {
        registerEvent(HttpEvent.CONNECT_FAILED);
    }

    @Override
    public void connectionAcquired(Call call, Connection connection) {
        registerEvent(HttpEvent.CONNECTION_ACQUIRED);
    }

    @Override
    public void connectionReleased(Call call, Connection connection) {
        registerEvent(HttpEvent.CONNECTION_RELEASED);
    }

    @Override
    public void requestHeadersStart(Call call) {
        registerEvent(HttpEvent.REQUEST_HEADERS_START);
    }

    @Override
    public void requestHeadersEnd(Call call, Request request) {
        registerEvent(HttpEvent.REQUEST_HEADERS_END);
    }

    @Override
    public void requestBodyStart(Call call) {
        registerEvent(HttpEvent.REQUEST_BODY_START);
    }

    @Override
    public void requestBodyEnd(Call call, long byteCount) {
        registerEvent(HttpEvent.REQUEST_BODY_END);
    }

    @Override
    public void requestFailed(Call call, IOException ioe) {
        registerEvent(HttpEvent.REQUEST_FAILED);
    }

    @Override
    public void responseHeadersStart(Call call) {
        registerEvent(HttpEvent.RESPONSE_HEADERS_START);
    }

    @Override
    public void responseHeadersEnd(Call call, Response response) {
        registerEvent(HttpEvent.RESPONSE_HEADERS_END);
    }

    @Override
    public void responseBodyStart(Call call) {
        registerEvent(HttpEvent.RESPONSE_BODY_START);
    }

    @Override
    public void responseBodyEnd(Call call, long byteCount) {
        registerEvent(HttpEvent.RESPONSE_BODY_END);
    }

    @Override
    public void responseFailed(Call call, IOException ioe) {
        registerEvent(HttpEvent.RESPONSE_FAILED);
    }

    @Override
    public void callEnd(Call call) {
        registerEvent(HttpEvent.CALL_END);
        propagateMetrics();
    }

    @Override
    public void callFailed(Call call, IOException ioe) {
        registerEvent(HttpEvent.CALL_FAILED);
        propagateMetrics();
    }

    @Override
    public void canceled(@NotNull Call call) {
        if (!finished) {
            // cancel is always called even if the request has finished successfully already
            // but if it is fired before callEnd or callFailed we should register it
            registerEvent(HttpEvent.CANCELED);
            propagateMetrics();
        }
    }

    private void registerEvent(HttpEvent eventName) {
        if (finished) {
            LOG.warn("Registered event after finished handler. event={}", eventName);
        }
        registerEvent(eventName, clock.millis() - startMs);
    }

    private void propagateMetrics() {
        finished = true;
        httpCallMetricsHandlers.forEach(strategy -> strategy.handleHttpCallMetrics(httpUrl, httpCallMetrics));
    }

    private void registerEvent(HttpEvent eventName, long relativeMs) {
        httpCallMetrics.computeIfAbsent(eventName, e -> new CopyOnWriteArrayList<>()).add(relativeMs);
    }
}
