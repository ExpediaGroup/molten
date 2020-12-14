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

import java.util.Map;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * A reactive cache implementation delegating to a regular {@link Map}.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
@RequiredArgsConstructor
public class ReactiveMapCache<K, V> implements ReactiveCache<K, V> {
    @NonNull
    private final Map<K, V> map;

    @Override
    public Mono<V> get(K key) {
        return Mono.just(key).flatMap(k -> Mono.justOrEmpty(map.get(key)));
    }

    @Override
    public Mono<Void> put(K key, V value) {
        return Mono.fromRunnable(() -> map.put(key, value));
    }
}
