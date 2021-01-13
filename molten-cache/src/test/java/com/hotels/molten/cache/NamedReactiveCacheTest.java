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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.function.Function;

import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit test for {@link NamedReactiveCache}.
 */
@Listeners(MockitoTestNGListener.class)
public class NamedReactiveCacheTest {
    private static final int KEY = 1;
    private static final String VALUE = "one";
    private static final String CACHENAME = "cachename";
    private static final Function<Integer, NamedCacheKey<Integer>> NAMED_CACHE_KEY_FACTORY = NamedCacheKey.forName(CACHENAME);
    private static final Duration TTL = Duration.ofSeconds(3);
    @Mock
    private ReactiveCache<NamedCacheKey<Integer>, CachedValue<String>> cache;
    private NamedReactiveCache<Integer, String> namedReactiveCache;

    @BeforeMethod
    public void initContext() {
        namedReactiveCache = new NamedReactiveCache<>(cache, CACHENAME, TTL);
    }

    @Test
    public void shouldDelegateGetWithNamedKeyLazily() {
        when(cache.get(NAMED_CACHE_KEY_FACTORY.apply(KEY))).thenReturn(Mono.just(CachedValue.just(VALUE)));
        StepVerifier.create(namedReactiveCache.get(KEY))
            .expectSubscription()
            .thenRequest(1)
            .expectNext(VALUE)
            .verifyComplete();
    }

    @Test
    public void shouldDelegatePutWithNamedKeyAndValueWithTTLLazily() {
        when(cache.put(NAMED_CACHE_KEY_FACTORY.apply(KEY), CachedValue.of(VALUE, TTL))).thenReturn(Mono.empty());
        StepVerifier.create(namedReactiveCache.put(KEY, VALUE))
            .expectSubscription()
            .thenRequest(1)
            .then(() -> verify(cache).put(NAMED_CACHE_KEY_FACTORY.apply(KEY), CachedValue.of(VALUE, TTL)))
            .verifyComplete();
    }

}
