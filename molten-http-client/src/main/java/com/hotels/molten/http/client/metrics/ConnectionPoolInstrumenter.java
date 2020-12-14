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

import static com.hotels.molten.core.metrics.MetricsSupport.name;

import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import okhttp3.ConnectionPool;

import com.hotels.molten.core.metrics.MetricId;

/**
 * Exposes {@link ConnectionPool} metrics via {@link MeterRegistry}.
 */
@RequiredArgsConstructor
public class ConnectionPoolInstrumenter {
    @NonNull
    private final MeterRegistry meterRegistry;
    @NonNull
    private final String clientId;

    /**
     * Instruments a {@link ConnectionPool}. Adds gauges for total and idle connection counts.
     *
     * @param connectionPool the pool to instrument
     */
    public void instrument(ConnectionPool connectionPool) {
        gaugeFor("total", connectionPool, ConnectionPool::connectionCount);
        gaugeFor("idle", connectionPool, ConnectionPool::idleConnectionCount);
    }

    private <T> void gaugeFor(String name, T target, ToDoubleFunction<T> valueFunction) {
        MetricId.builder()
            .name("http_client_connection_pool_" + name + "_connections")
            .hierarchicalName(hierarchicalName(name))
            .tag(Tag.of("client", clientId))
            .build()
            .toGauge(target, valueFunction)
            .register(meterRegistry);
    }

    private String hierarchicalName(String configName) {
        return name("client", clientId, "http-connection-pool", configName);
    }
}
