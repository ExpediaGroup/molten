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
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import com.hotels.molten.cache.CachedValue;
import com.hotels.molten.cache.NamedCacheKey;
import com.hotels.molten.cache.NamedReactiveCache;
import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.cache.metrics.InstrumentedReactiveCache;

/**
 * Creates a specific reactive cache with some resiliency added.
 * Adds the following layers (inside out):
 * <ol>
 * <li>named cache - provides cache regions and TTL</li>
 * <li>timeout</li>
 * <li>circuit breaker</li>
 * <li>retries - optional, defaults to none</li>
 * <li>fail-safe - emits no errors, optional, defaults to true</li>
 * </ol>
 * Should be built using a shared reactive cache instance.
 * If fail-safe is disabled then it will wrap all errors emitted in {@link ReactiveCacheInvocationException}.
 *
 * @param <K> type of key
 * @param <V> type of value
 * @see NamedReactiveCache
 * @see ResilientReactiveCache
 */
public class ResilientReactiveCacheBuilder<K, V> {
    private static final Map<String, String> CACHES = new ConcurrentHashMap<>();
    private ReactiveCache<NamedCacheKey<K>, CachedValue<V>> sharedResilientCache;
    private String cacheName;
    private Duration ttl = Duration.ofHours(1);
    private Duration timeout = Duration.ofMillis(50);
    private Duration putTimeout;
    private FailSafeMode failsafe = FailSafeMode.VERBOSE;
    private RetryBackoffSpec retry;
    private int retries;
    private Duration retryDelay = Duration.ofMillis(5);
    private CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofMillis(1000))
        .permittedNumberOfCallsInHalfOpenState(2)
        .slidingWindow(100, 100, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .build();
    private MeterRegistry meterRegistry;

    /**
     * Creates a resilient cache builder.
     *
     * @param <KEY>   the key type
     * @param <VALUE> the value type
     * @return the builder
     */
    public static <KEY, VALUE> ResilientReactiveCacheBuilder<KEY, VALUE> builder() {
        return new ResilientReactiveCacheBuilder<>();
    }

    /**
     * Creates the cache with the builder's current settings.
     *
     * @return the resilient cache
     */
    public ReactiveCache<K, V> createCache() {
        checkArgument(!isNullOrEmpty(cacheName), "Cache name must have value");
        checkArgument(CACHES.putIfAbsent(cacheName, cacheName) == null, "The cache name=" + cacheName + " has been already used");
        requireNonNull(meterRegistry);
        ReactiveCache<K, V> cache = new NamedReactiveCache<>(sharedResilientCache, cacheName, ttl);
        cache = new ResilientReactiveCache<>(cache, cacheName, timeout, putTimeout, circuitBreakerConfig, meterRegistry);
        cache = new InstrumentedReactiveCache<>(cache, meterRegistry, cacheName, "single");
        if (retry != null) {
            cache = new RetryingReactiveCache<>(cache, retry, meterRegistry, cacheName);
        }
        cache = new InstrumentedReactiveCache<>(cache, meterRegistry, cacheName, "total");
        if (failsafe.isFailsafe()) {
            cache = new FailSafeReactiveCache<>(cache, failsafe);
        } else {
            cache = new ConsolidatedExceptionHandlingReactiveCache<>(cache);
        }
        return cache;
    }

    /**
     * Sets the cache to delegate calls to. Usually a shared (remote) cache instance.
     *
     * @param reactiveCache the cache to wrap
     * @return this builder instance
     */
    public ResilientReactiveCacheBuilder<K, V> over(ReactiveCache<NamedCacheKey<K>, CachedValue<V>> reactiveCache) {
        sharedResilientCache = requireNonNull(reactiveCache);
        return this;
    }

    /**
     * Sets the name of the cache.
     *
     * @param cacheName the cache name
     * @return this builder instance
     */
    public ResilientReactiveCacheBuilder<K, V> withCacheName(String cacheName) {
        checkArgument(!isNullOrEmpty(cacheName), "Cache name must have value");
        this.cacheName = cacheName;
        return this;
    }

    /**
     * Sets the TTL of the entries.
     *
     * @param ttl the cache name
     * @return this builder instance
     */
    public ResilientReactiveCacheBuilder<K, V> withTTL(Duration ttl) {
        checkArgument(ttl != null && !ttl.isNegative(), "TTL must be positive but was " + ttl);
        this.ttl = ttl;
        return this;
    }

    /**
     * Sets the timeout for the cache calls.
     * If {@link #withPutTimeout putTimeout} is set, it's for 'get' calls only.
     *
     * @param timeout the timeout
     * @return this builder instance
     */
    public ResilientReactiveCacheBuilder<K, V> withTimeout(Duration timeout) {
        checkArgument(timeout != null && !timeout.isNegative(), "Timeout must be positive but was " + timeout);
        this.timeout = timeout;
        return this;
    }

    /**
     * Sets the timeout for the cache 'put' calls only.
     *
     * @param putTimeout the timeout
     * @return this builder instance
     */
    public ResilientReactiveCacheBuilder<K, V> withPutTimeout(Duration putTimeout) {
        checkArgument(putTimeout != null && !putTimeout.isNegative(), "Timeout must be positive but was " + putTimeout);
        this.putTimeout = putTimeout;
        return this;
    }

    /**
     * Sets the level of fail-safety of calls to this cache.
     *
     * @param val the level of fail-safety
     * @return this builder instance
     */
    public ResilientReactiveCacheBuilder<K, V> withFailsafe(FailSafeMode val) {
        failsafe = requireNonNull(val);
        return this;
    }

    /**
     * Sets the retry strategy for calls to this cache.
     *
     * @param retrySpec the retry spec
     * @return this builder instance
     */
    public ResilientReactiveCacheBuilder<K, V> withRetry(RetryBackoffSpec retrySpec) {
        checkArgument(retrySpec != null, "Retry spec must be non-null");
        retry = retrySpec;
        return this;
    }

    /**
     * Sets the number of retries for calls to this cache.
     *
     * @param retries the number of retries
     * @return this builder instance
     * @deprecated use {@link #withRetry(RetryBackoffSpec)}
     */
    @Deprecated
    public ResilientReactiveCacheBuilder<K, V> withRetries(int retries) {
        checkArgument(retries >= 0, "Number of retries must be non-negative but was " + retries);
        this.retries = retries;
        retry = retries == 0 ? null : Retry.backoff(retries, retryDelay);
        return this;
    }

    /**
     * Sets the fixed delay between retries for calls to this cache.
     *
     * @param retryDelay the interval between retries
     * @return this builder instance
     * @deprecated use {@link #withRetry(RetryBackoffSpec)}
     */
    @Deprecated
    public ResilientReactiveCacheBuilder<K, V> withRetryDelay(Duration retryDelay) {
        checkArgument(retryDelay.toMillis() > 0, "Retry delay must be non-negative but was " + retryDelay);
        this.retryDelay = retryDelay;
        retry = retries == 0 ? null : Retry.backoff(retries, retryDelay);
        return this;
    }

    /**
     * Sets the configuration of the circuitbreaker over cache calls.
     *
     * @param circuitBreakerConfig the circuitbreaker configuration
     * @return this builder instance
     */
    public ResilientReactiveCacheBuilder<K, V> withCircuitBreakerConfig(CircuitBreakerConfig circuitBreakerConfig) {
        checkArgument(circuitBreakerConfig != null, "Circuit breaker config must be non-null");
        this.circuitBreakerConfig = circuitBreakerConfig;
        return this;
    }

    /**
     * Sets the meter registry to register the meters of the resilient cache calls to.
     *
     * @param meterRegistry the meter registry
     * @return the builder instance
     */
    public ResilientReactiveCacheBuilder<K, V> withMeterRegistry(MeterRegistry meterRegistry) {
        checkArgument(meterRegistry != null, "Metric registry must be non-null");
        this.meterRegistry = meterRegistry;
        return this;
    }
}
