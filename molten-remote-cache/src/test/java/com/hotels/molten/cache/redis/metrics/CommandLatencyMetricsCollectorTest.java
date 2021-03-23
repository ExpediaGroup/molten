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

package com.hotels.molten.cache.redis.metrics;

import static com.hotels.molten.core.metrics.MetricsSupport.GRAPHITE_ID;
import static com.hotels.molten.core.metrics.MetricsSupport.name;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.lettuce.core.protocol.ProtocolKeyword;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Unit test for {@link CommandLatencyMetricsCollector}.
 */
@ExtendWith(MockitoExtension.class)
public class CommandLatencyMetricsCollectorTest {
    private static final String QUALIFIER = "qualifier";
    private static final String REMOTE_HOST = "remote.host";
    private static final int PORT = 1234;
    private static final String COMMAND_TYPE = "cmd";
    private MeterRegistry meterRegistry;
    @Mock
    private InetSocketAddress localAddress;
    @Mock
    private InetSocketAddress remoteAddress;
    @Mock
    private ProtocolKeyword commandType;

    @BeforeEach
    public void initContext() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    public void should_report_latencies_as_hierarchical_metrics() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        when(remoteAddress.getHostString()).thenReturn(REMOTE_HOST);
        when(remoteAddress.getPort()).thenReturn(PORT);
        when(commandType.name()).thenReturn(COMMAND_TYPE);
        // When
        new CommandLatencyMetricsCollector(meterRegistry, QUALIFIER).recordCommandLatency(localAddress, remoteAddress, commandType, 123L, 234L);
        // Then
        assertThat(meterRegistry.get(name(QUALIFIER, "clientLatencies", "remote_host_" + PORT, COMMAND_TYPE, "firstResponse")).timer().totalTime(TimeUnit.NANOSECONDS))
            .isEqualTo(123D);
        assertThat(meterRegistry.get(name(QUALIFIER, "clientLatencies", "remote_host_" + PORT, COMMAND_TYPE, "completion")).timer().totalTime(TimeUnit.NANOSECONDS))
            .isEqualTo(234D);
    }

    @Test
    public void should_report_latencies_as_dimensional_metrics() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        when(remoteAddress.getHostString()).thenReturn(REMOTE_HOST);
        when(remoteAddress.getPort()).thenReturn(PORT);
        when(commandType.name()).thenReturn(COMMAND_TYPE);
        // When
        new CommandLatencyMetricsCollector(meterRegistry, QUALIFIER).recordCommandLatency(localAddress, remoteAddress, commandType, 123L, 234L);
        // Then
        assertThat(meterRegistry.get("cache_request_lettuce_command_latency")
            .tag("name", QUALIFIER)
            .tag("remote", REMOTE_HOST + ":" + PORT)
            .tag("command", COMMAND_TYPE)
            .tag("type", "firstResponse")
            .tag(GRAPHITE_ID, name(QUALIFIER, "clientLatencies", "remote_host_" + PORT, COMMAND_TYPE, "firstResponse"))
            .timer().totalTime(TimeUnit.NANOSECONDS))
            .isEqualTo(123D);
        assertThat(meterRegistry.get("cache_request_lettuce_command_latency")
            .tag("name", QUALIFIER)
            .tag("remote", REMOTE_HOST + ":" + PORT)
            .tag("command", COMMAND_TYPE)
            .tag("type", "completion")
            .tag(GRAPHITE_ID, name(QUALIFIER, "clientLatencies", "remote_host_" + PORT, COMMAND_TYPE, "completion"))
            .timer().totalTime(TimeUnit.NANOSECONDS))
            .isEqualTo(234D);
    }
}
