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

import static java.util.Objects.requireNonNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nonnull;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import com.hotels.molten.core.metrics.MetricId;

/**
 * A Caffeine cache statistics counter which reports to dropwizard metrics.
 */
public class CaffeineCacheStatsInstrumenter implements StatsCounter {
    private final Counter hitCount;
    private final Counter missCount;
    private final Counter evictionCount;
    private final Timer loadSuccess;
    private final Timer loadFailure;
    private final LongAdder totalLoadTime = new LongAdder();
    private final LongAdder totalEvictedWeight = new LongAdder();
    private final TumblingWindowHitRatioMetrics hitRatioMetrics = new TumblingWindowHitRatioMetrics();
    private final MeterRegistry meterRegistry;
    private final MetricId metricId;

    public CaffeineCacheStatsInstrumenter(MeterRegistry meterRegistry, String metricsQualifier) {
        this.meterRegistry = requireNonNull(meterRegistry);
        this.metricId = MetricId.builder()
            .name("cache")
            .hierarchicalName(metricsQualifier)
            .tag(Tag.of("name", requireNonNull(metricsQualifier)))
            .tag(Tag.of("type", "single"))
            .build();
        hitCount = metricId.extendWith("hit_count", "hit-count").toCounter().register(meterRegistry);
        missCount = metricId.extendWith("miss_count", "miss-count").toCounter().register(meterRegistry);
        loadSuccess = metricId.extendWith("load_success", "load-success").toTimer().register(meterRegistry);
        loadFailure = metricId.extendWith("load_failure", "load-failure").toTimer().register(meterRegistry);
        evictionCount = metricId.extendWith("eviction_count", "eviction-count").toCounter().register(meterRegistry);
        metricId.extendWith("hit_ratio", "hit-ratio").toGauge(hitRatioMetrics, TumblingWindowHitRatioMetrics::hitRatio).register(meterRegistry);
    }

    @Override
    public void recordHits(int count) {
        hitCount.increment(count);
        hitRatioMetrics.registerHit(count);
    }

    @Override
    public void recordMisses(int count) {
        missCount.increment(count);
        hitRatioMetrics.registerMiss(count);
    }

    @Override
    public void recordLoadSuccess(long loadTime) {
        loadSuccess.record(loadTime, TimeUnit.NANOSECONDS);
        totalLoadTime.add(loadTime);
    }

    @Override
    public void recordLoadFailure(long loadTime) {
        loadFailure.record(loadTime, TimeUnit.NANOSECONDS);
        totalLoadTime.add(loadTime);
    }

    @Deprecated
    @Override
    public void recordEviction() {
    }

    @Deprecated
    @Override
    public void recordEviction(int weight) {
        evictionCount.increment();
        totalEvictedWeight.add(weight);
    }

    @Override
    public void recordEviction(int weight, RemovalCause cause) {
        evictionCount.increment();
        totalEvictedWeight.add(weight);
    }

    @Nonnull
    @Override
    public CacheStats snapshot() {
        return new CacheStats(Double.valueOf(hitCount.count()).longValue(), Double.valueOf(missCount.count()).longValue(), loadSuccess.count(), loadFailure.count(),
            totalLoadTime.longValue(), Double.valueOf(evictionCount.count()).longValue(), totalEvictedWeight.longValue());
    }

    /**
     * Registers the related {@link Cache} to report its size as well.
     *
     * @param cache the cache to register size gauge for
     */
    public void registerCache(Cache cache) {
        metricId.extendWith("size").toGauge(cache, Cache::estimatedSize).register(meterRegistry);
    }
}
