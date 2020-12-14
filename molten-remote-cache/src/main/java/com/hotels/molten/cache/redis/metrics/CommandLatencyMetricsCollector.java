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

import static com.hotels.molten.core.metrics.MetricsSupport.name;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import io.lettuce.core.metrics.CommandLatencyCollector;
import io.lettuce.core.metrics.CommandLatencyId;
import io.lettuce.core.metrics.CommandMetrics;
import io.lettuce.core.protocol.ProtocolKeyword;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import com.hotels.molten.core.metrics.MetricId;

/**
 * Collects command latencies with {@link MeterRegistry}.
 */
@RequiredArgsConstructor
public class CommandLatencyMetricsCollector implements CommandLatencyCollector {
    private static final String DOT = ".";
    private static final String UNDERSCORE = "_";

    @NonNull
    private final MeterRegistry meterRegistry;
    @NonNull
    private final String qualifier;

    @Override
    public void recordCommandLatency(SocketAddress local, SocketAddress remote, ProtocolKeyword commandType, long firstResponseLatency, long completionLatency) {
        getTimer(remote, commandType, "firstResponse").record(Duration.ofNanos(firstResponseLatency));
        getTimer(remote, commandType, "completion").record(Duration.ofNanos(completionLatency));
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Map<CommandLatencyId, CommandMetrics> retrieveMetrics() {
        return Collections.emptyMap();
    }

    @Override
    public void shutdown() {
    }

    private Timer getTimer(SocketAddress remote, ProtocolKeyword commandType, String metricName) {
        return MetricId.builder()
            .name("cache_request_lettuce_command_latency")
            .hierarchicalName(name(qualifier, "clientLatencies", parseRemoteAddress((InetSocketAddress) remote), commandType.name().toLowerCase(), metricName))
            .tag(Tag.of("name", qualifier))
            .tag(Tag.of("remote", ((InetSocketAddress) remote).getHostString() + ":" + ((InetSocketAddress) remote).getPort()))
            .tag(Tag.of("command", commandType.name()))
            .tag(Tag.of("type", metricName))
            .build()
            .toTimer()
            .register(meterRegistry);
    }

    private String parseRemoteAddress(InetSocketAddress inetSocketAddress) {
        return sanitise(inetSocketAddress.getHostString()) + UNDERSCORE + inetSocketAddress.getPort();
    }

    private String sanitise(final String value) {
        return value.replace(DOT, UNDERSCORE);
    }
}
