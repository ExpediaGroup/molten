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

package com.hotels.molten.cache.metrics;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.hotels.molten.core.metrics.MetricsSupport.name;
import static java.util.Objects.requireNonNull;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import reactor.core.publisher.Mono;

import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.core.metrics.MetricId;
import com.hotels.molten.metrics.ReactorInstrument;

/**
 * A reactive cache wrapper to instrument operations.
 * A {@link io.micrometer.core.instrument.Timer} is registered for both get and put with the given {@code cacheName} and {@code qualifier}.
 * e.g. if the cacheName is {@code remote-cache.someCache} and the {@code qualifier} is {@code total} then metrics will be like
 * <br> {@code remote-cache.someCache.get.total.success} for hierarchical metrics
 * <br> and {@code cache_requests(name=remote-cache.someCache, operation=get, type=total, status=success)} for dimensional metrics.
 *
 * Doesn't differentiate exceptions. Handles all of them as failures.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
public class InstrumentedReactiveCache<K, V> implements ReactiveCache<K, V> {
    private static final String HIERARCHICAL_METRICS_PREFIX = "reactive-cache";
    private final ReactiveCache<K, V> cache;
    private final ReactorInstrument getInstrumenter;
    private final ReactorInstrument putInstrumenter;
    private final TumblingWindowHitRatioMetrics hitRatioMetrics = new TumblingWindowHitRatioMetrics();

    public InstrumentedReactiveCache(ReactiveCache<K, V> cache, MeterRegistry meterRegistry, String cacheName, String qualifier) {
        checkArgument(!isNullOrEmpty(cacheName), "cacheName must have value");
        checkArgument(!isNullOrEmpty(qualifier), "qualifier must have value");
        this.cache = requireNonNull(cache);
        requireNonNull(meterRegistry);
        var getId = MetricId.builder()
            .name("cache_requests")
            .hierarchicalName(name(HIERARCHICAL_METRICS_PREFIX, cacheName, "get", qualifier))
            .tag(Tag.of("name", cacheName))
            .tag(Tag.of("operation", "get"))
            .tag(Tag.of("type", qualifier))
            .build();
        getInstrumenter = ReactorInstrument.builder().meterRegistry(meterRegistry).metricId(getId).build();
        var putId = MetricId.builder()
            .name("cache_requests")
            .hierarchicalName(name(HIERARCHICAL_METRICS_PREFIX, cacheName, "put", qualifier))
            .tag(Tag.of("name", cacheName))
            .tag(Tag.of("operation", "put"))
            .tag(Tag.of("type", qualifier))
            .build();
        putInstrumenter = ReactorInstrument.builder().meterRegistry(meterRegistry).metricId(putId).build();
        MetricId.builder()
            .name("cache_hit_ratio")
            .hierarchicalName(name(HIERARCHICAL_METRICS_PREFIX, cacheName, "hit-ratio", qualifier))
            .tag(Tag.of("name", cacheName))
            .tag(Tag.of("type", qualifier))
            .build()
            .toGauge(hitRatioMetrics, TumblingWindowHitRatioMetrics::hitRatio)
            .register(meterRegistry);
    }

    @Override
    public Mono<V> get(K key) {
        return cache.get(key)
            .doOnSuccess(s -> {
                if (s != null) {
                    hitRatioMetrics.registerHit();
                } else {
                    hitRatioMetrics.registerMiss();
                }
            })
            .doOnError(e -> hitRatioMetrics.registerMiss())
            .transform(getInstrumenter::mono);
    }

    @Override
    public Mono<Void> put(K key, V value) {
        return cache.put(key, value)
            .transform(putInstrumenter::mono);
    }
}
