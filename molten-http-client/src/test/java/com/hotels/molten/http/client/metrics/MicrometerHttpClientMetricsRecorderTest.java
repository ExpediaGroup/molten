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

import static com.hotels.molten.core.metrics.MetricsSupport.GRAPHITE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Unit test for {@link MicrometerHttpClientMetricsRecorder}.
 */
@Listeners(MockitoTestNGListener.class)
public class MicrometerHttpClientMetricsRecorderTest {
    private static final String CLIENT_ID = "clientId";
    private MicrometerHttpClientMetricsRecorder recorder;
    private MeterRegistry meterRegistry;

    @BeforeMethod
    public void initContext() {
        meterRegistry = new SimpleMeterRegistry();
        recorder = new MicrometerHttpClientMetricsRecorder(meterRegistry, CLIENT_ID);
    }

    @AfterMethod
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @Test
    public void should_register_hierarchical_metrics() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        // When
        recorder.recordConnectTime(mock(SocketAddress.class), Duration.ofMillis(123), "ok");
        // Then
        assertThat(meterRegistry.get("client.clientId.http-trace.duration.connect").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(123D);
    }

    @Test
    public void should_register_dimensional_metrics() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        // When
        recorder.recordConnectTime(mock(SocketAddress.class), Duration.ofMillis(123), "ok");
        // Then
        assertThat(meterRegistry.get("http_client_request_trace_duration")
            .tag("client", CLIENT_ID)
            .tag("event", "connect")
            .tag(GRAPHITE_ID, "client.clientId.http-trace.duration.connect")
            .timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(123D);
    }
}
