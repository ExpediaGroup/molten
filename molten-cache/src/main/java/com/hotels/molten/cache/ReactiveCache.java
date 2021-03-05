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

import java.util.function.Function;

import reactor.core.publisher.Mono;

/**
 * A reactive cache API.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
public interface ReactiveCache<K, V> {

    /**
     * Returns the value associated with this {@code key} if present.
     *
     * @param key the key to retrieve value for
     * @return the value if present, otherwise empty
     */
    Mono<V> get(K key);

    /**
     * Associates {@code value} with {@code key} in this cache. If the cache previously contained a
     * value associated with {@code key}, the old value is replaced by {@code value}.
     *
     * @param key   the key to set value for
     * @param value the value to store
     * @return a completion once the operation has finished
     */
    Mono<Void> put(K key, V value);

    /**
     * Return the value stored to {@code key} if any,
     * otherwise subscribe to the given {@code Mono}
     * and store its return value in the cache.
     *
     * <pre>
     * return Mono.fromCallable(() -&gt; expensiveServiceCall(key))
     *   .as(serviceCallCache.withKey(key));
     * </pre>
     *
     * @param key the cache key
     * @return a function
     */
    default Function<Mono<V>, Mono<V>> withKey(K key) {
        return withKey(key, Function.identity(), Function.identity());
    }

    /**
     * Return {@code valueFromConverter.apply(value)}, if {@code value} already stored to {@code key},
     * otherwise subscribe to the given {@code Mono}
     * and store its return value as {@code valueToConverter.apply(returnValue)} in the cache.
     *
     * <p>Useful for negative cache implementations.</p>
     *
     * @param key the cache key
     * @param valueToConverter transforming the non-cached value to be storable, must not return null
     * @param valueFromConverter transforming the cached value
     * @param <U> type of the exposed value
     * @return a function
     */
    default <U> Function<Mono<U>, Mono<U>> withKey(K key,
                                                   Function<U, V> valueToConverter,
                                                   Function<V, U> valueFromConverter) {
        return nonCachedMono -> get(key)
            .switchIfEmpty(nonCachedMono
                .map(valueToConverter)
                .switchIfEmpty(Mono.defer(() -> Mono.just(valueToConverter.apply(null))))
                .flatMap(nonCachedWrapped -> put(key, nonCachedWrapped)
                    .thenReturn(nonCachedWrapped)))
            .flatMap(value -> Mono.justOrEmpty(valueFromConverter.apply(value)));
    }
}
