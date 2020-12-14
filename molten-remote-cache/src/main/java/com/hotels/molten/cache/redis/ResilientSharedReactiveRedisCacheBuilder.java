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

package com.hotels.molten.cache.redis;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

import java.util.function.Supplier;

import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.micrometer.core.instrument.MeterRegistry;

import com.hotels.molten.cache.CachedValue;
import com.hotels.molten.cache.NamedCacheKey;
import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.cache.resilience.ResilientSharedReactiveCache;

/**
 * Creates a shared Redis based reactive cache with some resiliency added.
 * Utilizes a generic Redis connection which should ensure that it can encode/decode the used types.
 * Adds the following layers (inside out):
 * <ol>
 * <li>Redis cache</li>
 * <li>bulkhead - limits concurrency</li>
 * </ol>
 * This instance should be shared among specific cache instances.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 * @see ReactiveRemoteRedisCache
 * @see ResilientSharedReactiveCache
 */
public class ResilientSharedReactiveRedisCacheBuilder<K, V> {
    private Supplier<StatefulRedisClusterConnection<Object, Object>> redisConnectionSupplier;
    private String sharedCacheName = "sharedRemoteCache";
    private int maxConcurrency = 100;
    private MeterRegistry meterRegistry;

    public static <KEY, VALUE> ResilientSharedReactiveRedisCacheBuilder<KEY, VALUE> builder() {
        return new ResilientSharedReactiveRedisCacheBuilder<>();
    }

    public ReactiveCache<NamedCacheKey<K>, CachedValue<V>> createCache() {
        ReactiveRemoteRedisCache<K, V> reactiveRemoteRedisCache = new ReactiveRemoteRedisCache<>(new RetryingRedisConnectionProvider(redisConnectionSupplier));
        return new ResilientSharedReactiveCache<>(reactiveRemoteRedisCache, sharedCacheName, maxConcurrency, meterRegistry);
    }

    public ResilientSharedReactiveRedisCacheBuilder<K, V> withRedisConnection(Supplier<StatefulRedisClusterConnection<Object, Object>> redisConnectionSupplier) {
        this.redisConnectionSupplier = requireNonNull(redisConnectionSupplier);
        return this;
    }

    public ResilientSharedReactiveRedisCacheBuilder<K, V> withSharedCacheName(String val) {
        checkArgument(!isNullOrEmpty(sharedCacheName), "Cache name must have value");
        sharedCacheName = val;
        return this;
    }

    public ResilientSharedReactiveRedisCacheBuilder<K, V> withMaxConcurrency(int val) {
        checkArgument(val > 0, "Maximum concurrency must be positive but it was " + val);
        maxConcurrency = val;
        return this;
    }

    public ResilientSharedReactiveRedisCacheBuilder<K, V> withMeterRegistry(MeterRegistry val) {
        checkArgument(val != null, "Meter registry must be non-null");
        meterRegistry = val;
        return this;
    }
}
