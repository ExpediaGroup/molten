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

package com.hotels.molten.cache.resilience;

import static com.google.common.base.Preconditions.checkState;
import static com.hotels.molten.core.metrics.MetricsSupport.name;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.metrics.resilience.BulkheadInstrumenter;

/**
 * A resilient shared {@link ReactiveCache} with configurable bulkhead. Should be used over a shared singleton generic cache instance.
 * For the bulkhead the maximum concurrency can be controlled. If the bulkhead is full it won't wait for permission before rejecting the call.
 *
 * <br>
 * The {@code cacheName} is used for annotating exposed metrics. The same name cannot be reused twice.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
public class ResilientSharedReactiveCache<K, V> implements ReactiveCache<K, V> {
    private static final String HIERARCHICAL_METRICS_PREFIX = "reactive-cache";
    private static final Set<String> USED_CACHE_NAMES = ConcurrentHashMap.newKeySet();
    private final ReactiveCache<K, V> sharedCache;
    private final Bulkhead getBulkhead;
    private final Bulkhead putBulkhead;

    public ResilientSharedReactiveCache(ReactiveCache<K, V> sharedCache, String cacheName, int maxConcurrency, MeterRegistry meterRegistry) {
        this.sharedCache = requireNonNull(sharedCache);
        requireNonNull(cacheName);
        checkState(USED_CACHE_NAMES.add(cacheName), "The cache name=" + cacheName + " cannot be reused.");
        checkState(maxConcurrency > 0, "max concurrency must be positive");

        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(maxConcurrency)
            .maxWaitDuration(Duration.ZERO)
            .build();
        getBulkhead = createBulkhead(cacheName, "get", bulkheadConfig, meterRegistry);
        putBulkhead = createBulkhead(cacheName, "put", bulkheadConfig, meterRegistry);
    }

    @Override
    public Mono<V> get(K key) {
        return sharedCache.get(key)
            .transform(BulkheadOperator.of(getBulkhead));
    }

    @Override
    public Mono<Void> put(K key, V value) {
        return sharedCache.put(key, value)
            .transform(BulkheadOperator.of(putBulkhead));
    }

    private Bulkhead createBulkhead(String cacheName, String operation, BulkheadConfig bulkheadConfig, MeterRegistry meterRegistry) {
        Bulkhead bulkhead = Bulkhead.of(cacheName, bulkheadConfig);
        if (meterRegistry != null) {
            BulkheadInstrumenter.builder()
                .meterRegistry(meterRegistry)
                .metricsQualifier("cache_request_bulkhead")
                .componentTagName("name")
                .componentTagValue(cacheName)
                .operationTagName("operation")
                .operationTagValue(operation)
                .hierarchicalMetricsQualifier(name(HIERARCHICAL_METRICS_PREFIX, cacheName, operation))
                .build()
                .instrument(bulkhead);
        }
        return bulkhead;
    }
}
