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

import static com.hotels.molten.core.metrics.MetricsSupport.GRAPHITE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableMultimap;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.HttpUrl;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Unit test for {@link HttpMetricsReporterHandler}.
 */
@Listeners(MockitoTestNGListener.class)
public class HttpMetricsReporterHandlerTest {
    private static final String CLIENT_ID = "clientId";
    private MeterRegistry meterRegistry;
    @Mock
    private HttpUrl httpUrl;
    private HttpMetricsReporterHandler handler;

    @BeforeMethod
    public void init() {
        meterRegistry = new SimpleMeterRegistry();
        handler = new HttpMetricsReporterHandler(meterRegistry, CLIENT_ID);
    }

    @AfterMethod
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @Test
    public void should_report_all_event_timings_as_hierarchical_metric() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        var events = ImmutableMultimap.<HttpEvent, Long>builder()
            .put(HttpEvent.CALL_START, 0L)
            .put(HttpEvent.DNS_START, 100L)
            .put(HttpEvent.DNS_END, 150L)
            .put(HttpEvent.CONNECT_START, 200L)
            .put(HttpEvent.CANCELED, 210L)
            .put(HttpEvent.CALL_FAILED, 300L)
            .build();
        // When
        handler.handleHttpCallMetrics(httpUrl, events.asMap());
        // Then
        assertThat(meterRegistry.get("client.clientId.http-trace.timing.callStart").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(0D);
        assertThat(meterRegistry.get("client.clientId.http-trace.timing.dnsStart").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(100D);
        assertThat(meterRegistry.get("client.clientId.http-trace.timing.dnsEnd").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(150D);
        assertThat(meterRegistry.get("client.clientId.http-trace.timing.connectStart").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(200D);
        assertThat(meterRegistry.get("client.clientId.http-trace.timing.canceled").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(210D);
        assertThat(meterRegistry.get("client.clientId.http-trace.timing.callFailed").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(300D);
    }

    @Test
    public void should_report_all_event_timings_as_dimensional_metric() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        var events = ImmutableMultimap.<HttpEvent, Long>builder()
            .put(HttpEvent.CALL_START, 0L)
            .put(HttpEvent.DNS_START, 100L)
            .put(HttpEvent.DNS_END, 150L)
            .put(HttpEvent.CONNECT_START, 200L)
            .put(HttpEvent.CANCELED, 210L)
            .put(HttpEvent.CALL_FAILED, 300L)
            .build();
        // When
        handler.handleHttpCallMetrics(httpUrl, events.asMap());
        // Then
        assertThat(meterRegistry.get("http_client_request_trace_timing")
            .tag("client", CLIENT_ID)
            .tag("event", "callStart")
            .tag(GRAPHITE_ID, "client.clientId.http-trace.timing.callStart")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(0D);
        assertThat(meterRegistry.get("http_client_request_trace_timing")
            .tag("client", CLIENT_ID)
            .tag("event", "dnsStart")
            .tag(GRAPHITE_ID, "client.clientId.http-trace.timing.dnsStart")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(100D);
        assertThat(meterRegistry.get("http_client_request_trace_timing")
            .tag("client", CLIENT_ID)
            .tag("event", "dnsEnd")
            .tag(GRAPHITE_ID, "client.clientId.http-trace.timing.dnsEnd")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(150D);
        assertThat(meterRegistry.get("http_client_request_trace_timing")
            .tag("client", CLIENT_ID)
            .tag("event", "connectStart")
            .tag(GRAPHITE_ID, "client.clientId.http-trace.timing.connectStart")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(200D);
        assertThat(meterRegistry.get("http_client_request_trace_timing")
            .tag("client", CLIENT_ID)
            .tag("event", "canceled")
            .tag(GRAPHITE_ID, "client.clientId.http-trace.timing.canceled")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(210D);
        assertThat(meterRegistry.get("http_client_request_trace_timing")
            .tag("client", CLIENT_ID)
            .tag("event", "callFailed")
            .tag(GRAPHITE_ID, "client.clientId.http-trace.timing.callFailed")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(300D);
    }
}
