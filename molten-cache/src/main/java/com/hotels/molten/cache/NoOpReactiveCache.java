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

import reactor.core.publisher.Mono;

/**
 * A dummy reactive cache not storing anything.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class NoOpReactiveCache<K, V> implements ReactiveCache<K, V> {
    @Override
    public Mono<V> get(K key) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> put(K key, V value) {
        return Mono.empty();
    }
}
