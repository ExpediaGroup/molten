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

import java.time.Duration;
import java.util.Map;

import lombok.NonNull;
import lombok.Value;
import reactor.core.publisher.Mono;
import reactor.test.scheduler.VirtualTimeScheduler;

/**
 * A reactive cache over a map with artificial delay of execution.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
@Value
public class DelayedReactiveMapCache<K, V> implements ReactiveCache<K, V> {
    public static final Duration GET_DELAY = Duration.ofMillis(50);
    public static final Duration PUT_DELAY = Duration.ofMillis(100);
    @NonNull
    private final Map<K, V> map;
    @NonNull
    private final VirtualTimeScheduler scheduler;

    @Override
    public Mono<V> get(K key) {
        return Mono.delay(GET_DELAY, scheduler).flatMap(d -> Mono.justOrEmpty(map.get(key)));
    }

    @Override
    public Mono<Void> put(K key, V value) {
        return Mono.delay(PUT_DELAY, scheduler).doOnSuccess(k -> map.put(key, value)).then();
    }
}
