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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@code ReactiveCache} contract to test the expected behaviours.
 */
public abstract class ReactiveCacheContract {

    private static final String VALUE = "one";
    private static final int KEY = 1;

    private ReactiveCache<Integer, String> reactiveCache;

    protected abstract <T> ReactiveCache<Integer, T> createCacheForContractTest();

    @BeforeMethod
    public void setUp() {
        reactiveCache = spy(createCacheForContractTest());
    }

    @Test
    public void shouldCacheValueViaOperator() {
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
    public void shouldConvertValueViaOperator() {
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
    public void shouldNotFailIfThereAreNoEmittedItems() {
        Mono.<String>empty()
            .as(reactiveCache.cachingWith(KEY))
            .as(StepVerifier::create)
            .expectSubscription()
            .thenRequest(1)
            .verifyComplete();
    }

    @Test
    public void shouldWorkAsNegativeCacheViaOperator() {
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
    private static class StringWrapper {
        private String value;
    }
}
