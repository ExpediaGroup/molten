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

import static java.util.Objects.requireNonNull;

import reactor.core.publisher.Mono;

import com.hotels.molten.cache.ReactiveCache;

/**
 * Wraps all errors in {@link ReactiveCacheInvocationException} with the cause.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
public class ConsolidatedExceptionHandlingReactiveCache<K, V> implements ReactiveCache<K, V> {
    private final ReactiveCache<K, V> reactiveCache;

    public ConsolidatedExceptionHandlingReactiveCache(ReactiveCache<K, V> reactiveCache) {
        this.reactiveCache = requireNonNull(reactiveCache);
    }

    @Override
    public Mono<V> get(K key) {
        return reactiveCache.get(key)
            .onErrorResume(throwable -> Mono.error(new ReactiveCacheInvocationException("Error invoking get for key=" + key, throwable)));
    }

    @Override
    public Mono<Void> put(K key, V value) {
        return reactiveCache.put(key, value)
            .onErrorResume(throwable -> Mono.error(new ReactiveCacheInvocationException("Error invoking put for key=" + key, throwable)));
    }
}
