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
import static com.hotels.molten.core.metrics.MetricsSupport.name;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.core.metrics.MetricId;

/**
 * Retrying {@link ReactiveCache} which can retry failed operations with optional delay.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
public class RetryingReactiveCache<K, V> implements ReactiveCache<K, V> {
    private static final Logger LOG = LoggerFactory.getLogger(RetryingReactiveCache.class);
    private final ReactiveCache<K, V> reactiveCache;
    private final MeterRegistry meterRegistry;
    private final String cacheName;
    private final RetryBackoffSpec retrySpec;

    public RetryingReactiveCache(ReactiveCache<K, V> reactiveCache, RetryBackoffSpec retrySpec, MeterRegistry meterRegistry, String cacheName) {
        this.reactiveCache = requireNonNull(reactiveCache);
        this.retrySpec = requireNonNull(retrySpec);
        this.meterRegistry = requireNonNull(meterRegistry);
        this.cacheName = requireNonNull(cacheName);
        checkArgument(!Strings.isNullOrEmpty(cacheName), "The cacheName must be non-empty string but was [" + cacheName + "]");
    }

    @Override
    public Mono<V> get(K key) {
        return reactiveCache.get(key).retryWhen(retryFor(key, "get"));
    }

    @Override
    public Mono<Void> put(K key, V value) {
        return reactiveCache.put(key, value).retryWhen(retryFor(key, "put"));
    }

    private Retry retryFor(K key, String method) {
        return retrySpec
            .doBeforeRetry(retry -> {
                long retryIndex = retry.totalRetries() + 1;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Retrying retry={} for method={} key={}", retryIndex, method, key, retry.failure());
                } else if (LOG.isWarnEnabled()) {
                    LOG.warn("Retrying retry={} for method={} key={} cause={}-{}", retryIndex, method, key, retry.failure().getClass().getName(), retry.failure().toString());
                }
                MetricId.builder()
                    .name("cache_request_retries")
                    .hierarchicalName(name("reactive-cache", cacheName, method, "retries", "retry" + retryIndex))
                    .tag(Tag.of("name", cacheName))
                    .tag(Tag.of("operation", method))
                    .tag(Tag.of("retry", "retry" + retryIndex))
                    .build()
                    .toCounter()
                    .register(meterRegistry)
                    .increment();
            })
            .onRetryExhaustedThrow((spec, retry) -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Giving up retries after retry={} for method={} key={}", retry.totalRetries(), method, key, retry.failure());
                } else if (LOG.isWarnEnabled()) {
                    LOG.warn("Giving up retries after retry={} for method={} key={}, cause={}", retry.totalRetries(), method, key, retry.failure().toString());
                }
                return retry.failure();
            });
    }
}
