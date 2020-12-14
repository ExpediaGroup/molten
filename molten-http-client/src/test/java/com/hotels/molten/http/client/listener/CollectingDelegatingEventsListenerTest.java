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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.collect.ImmutableMultimap;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.HttpUrl;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test for {@link CollectingDelegatingEventsListener}.
 */
public class CollectingDelegatingEventsListenerTest {

    @Mock
    private HttpCallMetricsHandler httpCallMetricsHandlerA;
    @Mock
    private HttpCallMetricsHandler httpCallMetricsHandlerB;
    @Mock
    private HttpUrl httpUrl;
    @Mock
    private Connection connection;
    @Mock
    private Call call;
    @Mock
    private Clock clock;

    @BeforeMethod
    public void init() {
        MockitoAnnotations.initMocks(this);
        AtomicLong now = new AtomicLong();
        when(clock.millis()).thenAnswer(ie -> now.getAndAdd(100L));
    }

    @Test
    public void should_create_proper_event_items() {
        // When
        AtomicLong now = new AtomicLong();
        when(clock.millis()).thenAnswer(ie -> now.getAndAdd(100L));
        var listener = new CollectingDelegatingEventsListener(httpUrl, List.of(httpCallMetricsHandlerA, httpCallMetricsHandlerB));
        listener.setClock(clock);

        listener.callStart(call);
        listener.proxySelectStart(call, httpUrl);
        listener.proxySelectEnd(call, httpUrl, List.of());
        listener.dnsStart(call, null);
        listener.dnsEnd(call, null, null);
        listener.connectStart(call, null, null);
        listener.secureConnectStart(call);
        listener.secureConnectEnd(call, null);
        listener.connectEnd(call, null, null, null);
        listener.connectionAcquired(call, connection);
        listener.requestHeadersStart(call);
        listener.requestHeadersEnd(call, null);
        listener.requestBodyStart(call);
        listener.requestBodyEnd(call, 0);
        listener.responseHeadersStart(call);
        listener.responseHeadersEnd(call, null);
        listener.responseBodyStart(call);
        listener.responseBodyEnd(call, 0);
        listener.responseFailed(call, null);
        listener.connectionReleased(call, connection);
        listener.callEnd(call);
        listener.canceled(call); //cancelled event should not be registered if the call finished already
        // Then
        var expectedEvents = ImmutableMultimap.<HttpEvent, Long>builder()
            .put(HttpEvent.CALL_START, 0L)
            .put(HttpEvent.PROXY_SELECT_START, 100L)
            .put(HttpEvent.PROXY_SELECT_END, 200L)
            .put(HttpEvent.DNS_START, 300L)
            .put(HttpEvent.DNS_END, 400L)
            .put(HttpEvent.CONNECT_START, 500L)
            .put(HttpEvent.SECURE_CONNECT_START, 600L)
            .put(HttpEvent.SECURE_CONNECT_END, 700L)
            .put(HttpEvent.CONNECT_END, 800L)
            .put(HttpEvent.CONNECTION_ACQUIRED, 900L)
            .put(HttpEvent.REQUEST_HEADERS_START, 1000L)
            .put(HttpEvent.REQUEST_HEADERS_END, 1100L)
            .put(HttpEvent.REQUEST_BODY_START, 1200L)
            .put(HttpEvent.REQUEST_BODY_END, 1300L)
            .put(HttpEvent.RESPONSE_HEADERS_START, 1400L)
            .put(HttpEvent.RESPONSE_HEADERS_END, 1500L)
            .put(HttpEvent.RESPONSE_BODY_START, 1600L)
            .put(HttpEvent.RESPONSE_BODY_END, 1700L)
            .put(HttpEvent.RESPONSE_FAILED, 1800L)
            .put(HttpEvent.CONNECTION_RELEASED, 1900L)
            .put(HttpEvent.CALL_END, 2000L)
            .build();
        verify(httpCallMetricsHandlerA).handleHttpCallMetrics(eq(httpUrl), eq(expectedEvents.asMap()));
        verify(httpCallMetricsHandlerB).handleHttpCallMetrics(eq(httpUrl), eq(expectedEvents.asMap()));

        reset(httpCallMetricsHandlerA, httpCallMetricsHandlerB);

        listener.callFailed(call, null);
        var expectedExtendedEvents = ImmutableMultimap.<HttpEvent, Long>builder().putAll(expectedEvents).put(HttpEvent.CALL_FAILED, 2100L).build().asMap();
        verify(httpCallMetricsHandlerA).handleHttpCallMetrics(eq(httpUrl), eq(expectedExtendedEvents));
        verify(httpCallMetricsHandlerB).handleHttpCallMetrics(eq(httpUrl), eq(expectedExtendedEvents));
    }

    @Test
    public void should_support_multiple_events_of_same_type() {
        // When
        AtomicLong now = new AtomicLong();
        when(clock.millis()).thenAnswer(ie -> now.getAndAdd(100L));
        var listener = new CollectingDelegatingEventsListener(httpUrl, List.of(httpCallMetricsHandlerA, httpCallMetricsHandlerB));
        listener.setClock(clock);

        listener.callStart(call);
        listener.dnsStart(call, null);
        listener.dnsEnd(call, null, null);
        listener.dnsStart(call, null);
        listener.dnsEnd(call, null, null);
        listener.connectStart(call, null, null);
        listener.callFailed(call, null);
        // Then
        var expectedEvents = ImmutableMultimap.<HttpEvent, Long>builder()
            .put(HttpEvent.CALL_START, 0L)
            .put(HttpEvent.DNS_START, 100L)
            .put(HttpEvent.DNS_END, 200L)
            .put(HttpEvent.DNS_START, 300L)
            .put(HttpEvent.DNS_END, 400L)
            .put(HttpEvent.CONNECT_START, 500L)
            .put(HttpEvent.CALL_FAILED, 600L)
            .build();
        verify(httpCallMetricsHandlerA).handleHttpCallMetrics(eq(httpUrl), eq(expectedEvents.asMap()));
        verify(httpCallMetricsHandlerB).handleHttpCallMetrics(eq(httpUrl), eq(expectedEvents.asMap()));
    }

    @Test
    public void should_register_cancelled_event_if_not_yet_finished() {
        // When
        AtomicLong now = new AtomicLong();
        when(clock.millis()).thenAnswer(ie -> now.getAndAdd(100L));
        var listener = new CollectingDelegatingEventsListener(httpUrl, List.of(httpCallMetricsHandlerA, httpCallMetricsHandlerB));
        listener.setClock(clock);

        listener.callStart(call);
        listener.proxySelectStart(call, httpUrl);
        listener.canceled(call);
        // Then
        var expectedEvents = ImmutableMultimap.<HttpEvent, Long>builder()
            .put(HttpEvent.CALL_START, 0L)
            .put(HttpEvent.PROXY_SELECT_START, 100L)
            .put(HttpEvent.CANCELED, 200L)
            .build();
        verify(httpCallMetricsHandlerA).handleHttpCallMetrics(eq(httpUrl), eq(expectedEvents.asMap()));
        verify(httpCallMetricsHandlerB).handleHttpCallMetrics(eq(httpUrl), eq(expectedEvents.asMap()));
    }

}
