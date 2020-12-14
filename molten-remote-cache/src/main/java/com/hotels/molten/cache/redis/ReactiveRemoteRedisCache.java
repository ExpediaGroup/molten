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

import java.time.Duration;
import java.util.Objects;

import io.lettuce.core.cluster.api.reactive.RedisAdvancedClusterReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import com.hotels.molten.cache.CachedValue;
import com.hotels.molten.cache.NamedCacheKey;
import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.core.MoltenCore;

/**
 * A reactive remote cache using Redis to store values.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
@SuppressWarnings("unchecked")
@Slf4j
public class ReactiveRemoteRedisCache<K, V> implements ReactiveCache<NamedCacheKey<K>, CachedValue<V>> {
    private volatile Mono<RedisAdvancedClusterReactiveCommands<NamedCacheKey<K>, V>> reactive = Mono.error(new IllegalStateException("Couldn't acquire a connection yet."));

    public ReactiveRemoteRedisCache(RetryingRedisConnectionProvider<K, V> connectionProvider) {
        connectionProvider.connect().subscribe(r -> reactive = Mono.just(r));
    }

    @Override
    public Mono<CachedValue<V>> get(NamedCacheKey<K> key) {
        return Mono.defer(() -> reactive.flatMap(r -> r.get(key))
            .filter(Objects::nonNull)
            .transform(MoltenCore.propagateContext()))
            .map(CachedValue::just)
            .publishOn(Schedulers.parallel());
    }

    @Override
    public Mono<Void> put(NamedCacheKey<K> key, CachedValue<V> value) {
        return Mono.defer(() -> reactive.flatMap(r -> r.setex(key, getTtlFrom(value).getSeconds(), value.getValue())
            .transform(MoltenCore.propagateContext())))
            .publishOn(Schedulers.parallel())
            .then();
    }

    private Duration getTtlFrom(CachedValue<V> value) {
        return value.getTTL().orElseThrow(() -> new IllegalArgumentException("TTL must be specified"));
    }
}
