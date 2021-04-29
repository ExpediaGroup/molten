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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import com.hotels.molten.cache.ReactiveCache;

/**
 * A fail-safe wrapper over a {@link ReactiveCache}. Returns empty results in case of error. Logs errors based on the {@link FailSafeMode}.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
public class FailSafeReactiveCache<K, V> implements ReactiveCache<K, V> {
    private static final Logger LOG = LoggerFactory.getLogger(FailSafeReactiveCache.class);
    private final ReactiveCache<K, V> reactiveCache;
    private final FailSafeMode mode;

    public FailSafeReactiveCache(ReactiveCache<K, V> reactiveCache, FailSafeMode mode) {
        this.reactiveCache = requireNonNull(reactiveCache);
        this.mode = requireNonNull(mode);
    }

    @Override
    public Mono<V> get(K key) {
        return reactiveCache.get(key).doOnError(e -> logErrorForKey("get", key, e)).onErrorResume(e -> Mono.empty());
    }

    @Override
    public Mono<Void> put(K key, V value) {
        return reactiveCache.put(key, value).doOnError(e -> logErrorForKey("put", key, e)).onErrorResume(e -> Mono.empty());
    }

    private void logErrorForKey(String operation, K key, Throwable e) {
        switch (mode) {
        case LOGGING:
            LOG.warn("Error invoking {} for key={} error={}", operation, key, e.toString());
            break;
        case VERBOSE:
            LOG.error("Error invoking {} for key={}", operation, key, e);
            break;
        default:
            // don't log anything
        }
    }
}
