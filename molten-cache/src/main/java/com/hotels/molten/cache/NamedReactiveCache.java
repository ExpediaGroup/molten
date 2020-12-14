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

package com.hotels.molten.cache;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.function.Function;

import reactor.core.publisher.Mono;

/**
 * A {@link ReactiveCache} implementation which stores entries with TTL (time to live) sharded by cache name.
 * <br>
 * Useful for cache implementations which share a common region (e.g. Redis, ElastiCache) and per entry TTL.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
public class NamedReactiveCache<K, V> implements ReactiveCache<K, V> {
    private final ReactiveCache<NamedCacheKey<K>, CachedValue<V>> sharedCache;
    private final Function<K, NamedCacheKey<K>> cacheKeyFactory;
    private final Function<V, CachedValue<V>> cachedValueFactory;

    public NamedReactiveCache(ReactiveCache<NamedCacheKey<K>, CachedValue<V>> sharedCache, String cacheName, Duration ttl) {
        this.sharedCache = requireNonNull(sharedCache);
        checkArgument(!isNullOrEmpty(cacheName), "Cache name must have value");
        cacheKeyFactory = NamedCacheKey.forName(cacheName);
        checkArgument(ttl.toMillis() > 0, "TTL must be positive");
        cachedValueFactory = val -> CachedValue.of(val, ttl);
    }

    @Override
    public Mono<V> get(K key) {
        return sharedCache.get(cacheKeyFactory.apply(key)).map(CachedValue::getValue);
    }

    @Override
    public Mono<Void> put(K key, V value) {
        return sharedCache.put(cacheKeyFactory.apply(key), cachedValueFactory.apply(value));
    }
}
