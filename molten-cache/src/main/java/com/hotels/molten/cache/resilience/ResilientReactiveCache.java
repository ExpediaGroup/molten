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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.hotels.molten.core.metrics.MetricsSupport.name;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nullable;

import com.google.common.base.Throwables;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import reactor.core.publisher.Mono;

import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.core.metrics.MetricId;
import com.hotels.molten.metrics.resilience.CircuitBreakerInstrumenter;

/**
 * Resilient {@link ReactiveCache} with timeout and circuit-breaker.
 * Consider using {@link ResilientReactiveCacheBuilder} to create a more robust resilient cache.<br>
 * Should be wrapped around an {@link ResilientSharedReactiveCache}.<br>
 * Please note that the provided {@link CircuitBreakerConfig} will be altered in a way it ignores {@link BulkheadFullException} in the causal chain as a failure.
 * Instruments circuit-breaker with {@link CircuitBreakerInstrumenter}.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 * @see ResilientSharedReactiveCache
 * @see ResilientReactiveCacheBuilder
 */
public class ResilientReactiveCache<K, V> implements ReactiveCache<K, V> {
    private static final String HIERARCHICAL_METRICS_PREFIX = "reactive-cache";
    private static final Set<String> USED_CACHE_NAMES = ConcurrentHashMap.newKeySet();
    private final ReactiveCache<K, V> reactiveCache;
    private final Duration getTimeout;
    private final Duration putTimeout;
    private final CircuitBreaker circuitBreaker;
    private final LongAdder timedOutGetCount = new LongAdder();
    private final LongAdder timedOutPutCount = new LongAdder();

    public ResilientReactiveCache(ReactiveCache<K, V> reactiveCache, String cacheName, Duration timeout, CircuitBreakerConfig circuitBreakerConfig, MeterRegistry meterRegistry) {
        this(reactiveCache, cacheName, timeout, null, circuitBreakerConfig, meterRegistry);
    }

    public ResilientReactiveCache(
        @NonNull ReactiveCache<K, V> reactiveCache,
        @NonNull String cacheName,
        @NonNull Duration timeout,
        @Nullable Duration putTimeout,
        @NonNull CircuitBreakerConfig circuitBreakerConfig,
        @NonNull MeterRegistry meterRegistry
    ) {
        this.reactiveCache = reactiveCache;
        checkState(USED_CACHE_NAMES.add(cacheName), "The cache name=" + cacheName + " cannot be reused.");
        this.getTimeout = validateTimeout(timeout);
        this.putTimeout = Optional.ofNullable(putTimeout).map(this::validateTimeout).orElse(timeout);
        CircuitBreakerConfig finalCircuitBreakerConfig = CircuitBreakerConfig.from(circuitBreakerConfig)
            .recordException(e -> Throwables.getCausalChain(e).stream().noneMatch(ex -> ex instanceof BulkheadFullException))
            .build();
        circuitBreaker = createCircuitBreaker(cacheName, meterRegistry, finalCircuitBreakerConfig);
        MetricId.builder()
            .name("cache_request_timeouts")
            .hierarchicalName(name(HIERARCHICAL_METRICS_PREFIX, cacheName, "get", "timeout"))
            .tag(Tag.of("name", cacheName))
            .tag(Tag.of("operation", "get"))
            .build()
            .toGauge(timedOutGetCount, LongAdder::longValue)
            .register(meterRegistry);
        MetricId.builder()
            .name("cache_request_timeouts")
            .hierarchicalName(name(HIERARCHICAL_METRICS_PREFIX, cacheName, "put", "timeout"))
            .tag(Tag.of("name", cacheName))
            .tag(Tag.of("operation", "put"))
            .build()
            .toGauge(timedOutPutCount, LongAdder::longValue)
            .register(meterRegistry);
    }

    @Override
    public Mono<V> get(K key) {
        return reactiveCache.get(key)
            .timeout(getTimeout)
            .doOnError(e -> {
                if (e instanceof TimeoutException) {
                    timedOutGetCount.increment();
                }
            })
            .transform(CircuitBreakerOperator.of(circuitBreaker));
    }

    @Override
    public Mono<Void> put(K key, V value) {
        return reactiveCache.put(key, value)
            .timeout(putTimeout)
            .doOnError(e -> {
                if (e instanceof TimeoutException) {
                    timedOutPutCount.increment();
                }
            })
            .transform(CircuitBreakerOperator.of(circuitBreaker));
    }

    private Duration validateTimeout(Duration timeout) {
        checkArgument(timeout.toMillis() > 0, "timeout must be positive but was " + timeout);
        return timeout;
    }

    private CircuitBreaker createCircuitBreaker(String cacheName, MeterRegistry meterRegistry, CircuitBreakerConfig circuitBreakerConfig) {
        CircuitBreaker circuitBreaker = CircuitBreaker.of(cacheName, circuitBreakerConfig);
        CircuitBreakerInstrumenter.builder()
            .meterRegistry(meterRegistry)
            .metricsQualifier("cache_request_circuit")
            .hierarchicalMetricsQualifier(name(HIERARCHICAL_METRICS_PREFIX, cacheName))
            .componentTagName("name")
            .componentTagValue(cacheName)
            .build()
            .instrument(circuitBreaker);
        return circuitBreaker;
    }
}
