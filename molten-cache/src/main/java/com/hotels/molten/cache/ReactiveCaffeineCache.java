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

import com.github.benmanes.caffeine.cache.Cache;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * A reactive cache implementation delegating to a Caffeine {@link Cache} cache.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
@RequiredArgsConstructor
public class ReactiveCaffeineCache<K, V> implements ReactiveCache<K, V> {
    @NonNull
    private final Cache<K, V> cache;

    @Override
    public Mono<V> get(K key) {
        return Mono.just(key).flatMap(k -> Mono.justOrEmpty(cache.getIfPresent(key)));
    }

    @Override
    public Mono<Void> put(K key, V value) {
        return Mono.fromRunnable(() -> cache.put(key, value));
    }
}


