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
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Test for {@link DurationMetricsReporterHandler}.
 */
public class DurationMetricsReporterHandlerTest {
    private static final String CLIENT_ID = "clientId";
    private MeterRegistry meterRegistry;
    @Mock
    private HttpUrl httpUrl;
    private DurationMetricsReporterHandler handler;

    @BeforeMethod
    public void init() {
        MockitoAnnotations.initMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        handler = new DurationMetricsReporterHandler(meterRegistry, CLIENT_ID);
    }

    @AfterMethod
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @Test
    public void should_report_hierarchical_metrics() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        var events = ImmutableMultimap.<HttpEvent, Long>builder()
            .put(HttpEvent.CALL_START, 0L)
            .put(HttpEvent.DNS_START, 100L)
            .put(HttpEvent.DNS_END, 150L)
            .put(HttpEvent.CONNECT_START, 200L)
            .put(HttpEvent.CONNECT_END, 220L)
            .put(HttpEvent.CONNECTION_ACQUIRED, 230L)
            .put(HttpEvent.CANCELED, 260L)
            .put(HttpEvent.CALL_FAILED, 300L)
            .build();
        // When
        handler.handleHttpCallMetrics(httpUrl, events.asMap());
        // Then
        assertThat(meterRegistry.get("client.clientId.http-trace.duration.connect").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(20D);
        assertThat(meterRegistry.get("client.clientId.http-trace.duration.connectionAcquired").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(230D);
        assertThat(meterRegistry.get("client.clientId.http-trace.duration.dns").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(50D);
        assertThat(meterRegistry.get("client.clientId.http-trace.duration.callFailed").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(300D);
        assertThat(meterRegistry.get("client.clientId.http-trace.duration.callCanceled").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(260D);
    }

    @Test
    public void should_report_dimensional_metrics() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        var events = ImmutableMultimap.<HttpEvent, Long>builder()
            .put(HttpEvent.CALL_START, 0L)
            .put(HttpEvent.DNS_START, 100L)
            .put(HttpEvent.DNS_END, 150L)
            .put(HttpEvent.CONNECT_START, 200L)
            .put(HttpEvent.CONNECT_END, 220L)
            .put(HttpEvent.CONNECTION_ACQUIRED, 230L)
            .put(HttpEvent.CANCELED, 260L)
            .put(HttpEvent.CALL_FAILED, 300L)
            .build();
        // When
        handler.handleHttpCallMetrics(httpUrl, events.asMap());
        // Then
        assertThat(meterRegistry.get("http_client_request_trace_duration")
            .tag("client", CLIENT_ID)
            .tag("event", "connect")
            .tag(GRAPHITE_ID, "client.clientId.http-trace.duration.connect")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(20D);
        assertThat(meterRegistry.get("http_client_request_trace_duration")
            .tag("client", CLIENT_ID)
            .tag("event", "connectionAcquired")
            .tag(GRAPHITE_ID, "client.clientId.http-trace.duration.connectionAcquired")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(230D);
        assertThat(meterRegistry.get("http_client_request_trace_duration")
            .tag("client", CLIENT_ID)
            .tag("event", "dns")
            .tag(GRAPHITE_ID, "client.clientId.http-trace.duration.dns")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(50D);
        assertThat(meterRegistry.get("http_client_request_trace_duration")
            .tag("client", CLIENT_ID)
            .tag("event", "callFailed")
            .tag(GRAPHITE_ID, "client.clientId.http-trace.duration.callFailed")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(300D);
        assertThat(meterRegistry.get("http_client_request_trace_duration")
            .tag("client", CLIENT_ID)
            .tag("event", "callCanceled")
            .tag(GRAPHITE_ID, "client.clientId.http-trace.duration.callCanceled")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(260D);
    }
}
