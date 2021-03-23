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

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import lombok.Value;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@code ReactiveCache} contract to test the expected behaviours.
 */
public interface ReactiveCacheContract {

    String VALUE = "one";
    int KEY = 1;

    <T> ReactiveCache<Integer, T> createCacheForContractTest();

    @Test
    default void should_cache_value_via_operator() {
        ReactiveCache<Integer, String> reactiveCache = spy(createCacheForContractTest());
        Mono.just(VALUE)
            .as(reactiveCache.cachingWith(KEY))
            .as(StepVerifier::create)
            .expectSubscription()
            .thenRequest(1)
            .expectNext(VALUE)
            .verifyComplete();

        Mono.<String>error(() -> new RuntimeException("Should not be called as the value is already in cache"))
            .as(reactiveCache.cachingWith(KEY))
            .as(StepVerifier::create)
            .expectSubscription()
            .thenRequest(1)
            .expectNext(VALUE)
            .verifyComplete();

        verify(reactiveCache, times(1)).put(KEY, VALUE);
    }

    @Test
    default void should_convert_value_via_operator() {
        ReactiveCache<Integer, String> reactiveCache = spy(createCacheForContractTest());
        Mono.just(VALUE)
            .as(reactiveCache.cachingWith(KEY, String::toUpperCase, String::toLowerCase))
            .as(StepVerifier::create)
            .expectSubscription()
            .thenRequest(1)
            .expectNext(VALUE)
            .verifyComplete();

        verify(reactiveCache, times(1)).put(KEY, "ONE");
    }

    @Test
    default void should_not_fail_if_there_are_no_emitted_items() {
        ReactiveCache<Integer, String> reactiveCache = createCacheForContractTest();
        Mono.<String>empty()
            .as(reactiveCache.cachingWith(KEY))
            .as(StepVerifier::create)
            .expectSubscription()
            .thenRequest(1)
            .verifyComplete();
    }

    @Test
    default void should_work_as_negative_cache_via_operator() {
        ReactiveCache<Integer, StringWrapper> reactiveNegativeCache = spy(createCacheForContractTest());
        Mono.<String>empty()
            .as(reactiveNegativeCache.cachingWith(KEY, StringWrapper::new, StringWrapper::getValue))
            .as(StepVerifier::create)
            .expectSubscription()
            .thenRequest(1)
            .verifyComplete();

        Mono.<String>error(() -> new RuntimeException("Should not be called as the value is already in cache"))
            .as(reactiveNegativeCache.cachingWith(KEY, StringWrapper::new, StringWrapper::getValue))
            .as(StepVerifier::create)
            .expectSubscription()
            .thenRequest(1)
            .verifyComplete();

        verify(reactiveNegativeCache, times(1)).put(KEY, new StringWrapper(null));
    }

    @Value
    class StringWrapper {
        private String value;
    }
}
