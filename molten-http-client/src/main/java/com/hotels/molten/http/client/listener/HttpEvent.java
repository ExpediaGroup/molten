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

import static java.util.Objects.requireNonNull;

import java.util.Optional;

import lombok.NonNull;

public enum HttpEvent {
    CALL_START("callStart"),
    PROXY_SELECT_START("proxySelectStart"),
    PROXY_SELECT_END("proxySelectEnd", PROXY_SELECT_START, "proxySelect"),
    DNS_START("dnsStart"),
    DNS_END("dnsEnd", DNS_START, "dns"),
    CONNECT_START("connectStart"),
    SECURE_CONNECT_START("secureConnectStart"),
    SECURE_CONNECT_END("secureConnectEnd", SECURE_CONNECT_START, "secureConnect"),
    CONNECT_FAILED("connectFailed", CONNECT_START, "connectFailed"),
    CONNECT_END("connectEnd", CONNECT_START, "connect"),
    CONNECTION_ACQUIRED("connectionAcquired", CALL_START, "connectionAcquired"),
    REQUEST_HEADERS_START("requestHeadersStart"),
    REQUEST_HEADERS_END("requestHeadersEnd", REQUEST_HEADERS_START, "requestHeaders"),
    REQUEST_BODY_START("requestBodyStart"),
    REQUEST_BODY_END("requestBodyEnd", REQUEST_BODY_START, "requestBody"),
    REQUEST_FAILED("requestFailed", REQUEST_HEADERS_START, "requestFailed"),
    RESPONSE_HEADERS_START("responseHeadersStart"),
    RESPONSE_HEADERS_END("responseHeadersEnd", RESPONSE_HEADERS_START, "responseHeaders"),
    RESPONSE_BODY_START("responseBodyStart"),
    RESPONSE_BODY_END("responseBodyEnd", RESPONSE_BODY_START, "responseBody"),
    RESPONSE_FAILED("responseFailed", RESPONSE_HEADERS_START, "responseFailed"),
    CONNECTION_RELEASED("connectionReleased", CONNECTION_ACQUIRED, "connectionHeld"),
    CALL_END("callEnd", CALL_START, "call"),
    CALL_FAILED("callFailed", CALL_START, "callFailed"),
    CANCELED("canceled", CALL_START, "callCanceled");

    @NonNull
    private final String id;
    private final HttpEvent fromEvent;
    private final String durationId;

    HttpEvent(@NonNull String id, HttpEvent fromEvent, String durationId) {
        this.id = requireNonNull(id);
        this.fromEvent = fromEvent;
        this.durationId = durationId;
    }

    HttpEvent(@NonNull String id) {
        this(id, null, null);
    }

    public String getId() {
        return id;
    }

    public Optional<HttpEvent> getFromEvent() {
        return Optional.ofNullable(fromEvent);
    }

    public Optional<String> getDurationId() {
        return Optional.ofNullable(durationId);
    }
}
