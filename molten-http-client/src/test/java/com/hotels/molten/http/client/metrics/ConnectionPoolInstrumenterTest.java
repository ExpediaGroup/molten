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
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Unit test for {@link ConnectionPoolInstrumenter}.
 */
@ExtendWith(MockitoExtension.class)
public class ConnectionPoolInstrumenterTest {
    private static final String CLIENT_ID = "clientId";
    private ConnectionPoolInstrumenter instrumenter;
    private MeterRegistry meterRegistry;
    @Mock
    private ConnectionPool connectionPool;

    @BeforeEach
    public void initContext() {
        meterRegistry = new SimpleMeterRegistry();
        instrumenter = new ConnectionPoolInstrumenter(meterRegistry, CLIENT_ID);
        when(connectionPool.connectionCount()).thenReturn(3);
        when(connectionPool.idleConnectionCount()).thenReturn(2);
    }

    @Test
    public void should_register_hierarchical_metrics() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        // When
        instrumenter.instrument(connectionPool);
        // Then
        assertThat(meterRegistry.get("client.clientId.http-connection-pool.total").gauge().value()).isEqualTo(3D);
        assertThat(meterRegistry.get("client.clientId.http-connection-pool.idle").gauge().value()).isEqualTo(2D);
    }

    @Test
    public void should_register_dimensional_metrics() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        // When
        instrumenter.instrument(connectionPool);
        // Then
        assertThat(meterRegistry.get("http_client_connection_pool_total_connections")
            .tag("client", CLIENT_ID)
            .tag(GRAPHITE_ID, "client.clientId.http-connection-pool.total")
            .gauge().value()).isEqualTo(3D);
        assertThat(meterRegistry.get("http_client_connection_pool_idle_connections")
            .tag("client", CLIENT_ID)
            .tag(GRAPHITE_ID, "client.clientId.http-connection-pool.idle")
            .gauge().value()).isEqualTo(2D);
    }
}
