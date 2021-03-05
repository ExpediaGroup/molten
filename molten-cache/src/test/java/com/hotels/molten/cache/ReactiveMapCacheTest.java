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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Value;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit test for {@link ReactiveMapCache}.
 */
@Listeners(MockitoTestNGListener.class)
public class ReactiveMapCacheTest {
    private static final String VALUE = "one";
    private static final int KEY = 1;
    @Mock
    private Map<Integer, String> cache;
    @InjectMocks
    private ReactiveMapCache<Integer, String> reactiveCache;

    @Test
    public void shouldDelegateGetLazily() {
        when(cache.get(KEY)).thenReturn(VALUE);
        Mono<String> get = reactiveCache.get(KEY);
        verifyNoInteractions(cache);
        StepVerifier.create(get)
            .expectSubscription()
            .thenRequest(1)
            .then(() -> verify(cache).get(KEY))
            .expectNext(VALUE)
            .verifyComplete();
    }

    @Test
    public void shouldDelegatePutLazily() {
        Mono<Void> put = reactiveCache.put(KEY, VALUE);
        verifyNoInteractions(cache);
        StepVerifier.create(put)
            .expectSubscription()
            .thenRequest(1)
            .then(() -> verify(cache).put(KEY, VALUE))
            .verifyComplete();
    }

    @Test
    public void shouldPutViaOperator() {
        Mono.just(VALUE)
            .as(reactiveCache.withKey(KEY))
            .as(StepVerifier::create)
            .expectSubscription()
            .thenRequest(1)
            .then(() -> verify(cache).put(KEY, VALUE))
            .expectNext(VALUE)
            .verifyComplete();
    }

    @Test
    public void shouldConvertValueViaOperator() {
        Mono.just(VALUE)
            .as(reactiveCache.withKey(KEY, String::toUpperCase, String::toLowerCase))
            .as(StepVerifier::create)
            .expectSubscription()
            .thenRequest(1)
            .then(() -> verify(cache).put(KEY, "ONE"))
            .expectNext(VALUE)
            .verifyComplete();
    }

    @Test
    public void shouldWorkAsNegativeCacheViaOperator() {
        Map<Integer, StringWrapper> negativeCache = new ConcurrentHashMap<>();
        ReactiveCache<Integer, StringWrapper> negativeReactiveCache = new ReactiveMapCache<>(negativeCache);
        Mono.<String>empty()
            .as(negativeReactiveCache.withKey(KEY, StringWrapper::new, StringWrapper::getValue))
            .as(StepVerifier::create)
            .expectSubscription()
            .thenRequest(1)
            .verifyComplete();
        assertThat(negativeCache).containsEntry(KEY, new StringWrapper(null));
    }

    @Value
    private static class StringWrapper {
        String value;
    }
}
