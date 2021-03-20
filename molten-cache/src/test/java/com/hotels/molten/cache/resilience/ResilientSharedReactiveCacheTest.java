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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.cache.ReactiveCacheTestContract;
import com.hotels.molten.cache.ReactiveMapCache;
import com.hotels.molten.test.AssertSubscriber;

/**
 * Unit test for {@link ResilientSharedReactiveCache}.
 */
@ExtendWith(MockitoExtension.class)
public class ResilientSharedReactiveCacheTest implements ReactiveCacheTestContract {
    private static final Long KEY = 1L;
    private static final String VALUE = "value";
    private static final String CACHE_NAME = "cacheName";
    private static final AtomicInteger IDX = new AtomicInteger();
    private ResilientSharedReactiveCache<Long, String> resilientCache;
    @Mock
    private ReactiveCache<Long, String> cache;
    private MeterRegistry meterRegistry;

    @Override
    public <T> ReactiveCache<Integer, T> createCacheForContractTest() {
        return getResilientCache(new ReactiveMapCache<>(new ConcurrentHashMap<>()), 2);
    }

    @BeforeEach
    public void initContext() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    public void should_delegate_get_call() {
        when(cache.get(KEY)).thenReturn(Mono.just(VALUE));

        StepVerifier.create(getResilientCache(cache, 2).get(KEY)).expectNext(VALUE).verifyComplete();

        verify(cache).get(KEY);
    }
    @Test
    public void should_limit_concurrency_for_get() {
        resilientCache = getResilientCache(cache, 1);
        when(cache.get(KEY)).thenReturn(Mono.delay(Duration.ofMillis(100)).map(i -> VALUE));

        AssertSubscriber<String> test1 = AssertSubscriber.create();
        AssertSubscriber<String> test2 = AssertSubscriber.create();
        resilientCache.get(KEY).subscribe(test1);
        resilientCache.get(KEY).subscribe(test2);
        test1.await().assertResult(VALUE);
        test2.await().assertError(BulkheadFullException.class);
    }

    @Test
    public void should_delegate_put_call() {
        when(cache.put(KEY, VALUE)).thenReturn(Mono.empty());

        StepVerifier.create(getResilientCache(cache, 2).put(KEY, VALUE)).verifyComplete();

        verify(cache).put(KEY, VALUE);
    }

    @Test
    public void should_limit_concurrency_for_put() {
        resilientCache = getResilientCache(cache, 1);
        when(cache.put(KEY, VALUE)).thenReturn(Mono.delay(Duration.ofMillis(100)).then());

        AssertSubscriber<Void> test1 = AssertSubscriber.create();
        AssertSubscriber<Void> test2 = AssertSubscriber.create();
        resilientCache.put(KEY, VALUE).subscribe(test1);
        resilientCache.put(KEY, VALUE).subscribe(test2);
        test1.await().assertComplete();
        test2.await().assertError(BulkheadFullException.class);
    }

    @Test
    public void should_have_separate_bulkhead_for_get_and_put() {
        resilientCache = getResilientCache(cache, 1);
        when(cache.get(KEY)).thenReturn(Mono.delay(Duration.ofMillis(100)).map(i -> VALUE));
        when(cache.put(KEY, VALUE)).thenReturn(Mono.delay(Duration.ofMillis(100)).then());

        AssertSubscriber<String> test1 = AssertSubscriber.create();
        AssertSubscriber<Void> test2 = AssertSubscriber.create();

        resilientCache.get(KEY).subscribe(test1);
        resilientCache.put(KEY, VALUE).subscribe(test2);

        test1.await().assertResult(VALUE);
        test2.await().assertComplete();
    }

    private <K, V> ResilientSharedReactiveCache<K, V> getResilientCache(ReactiveCache<K, V> reactiveCache, int maxConcurrency) {
        return new ResilientSharedReactiveCache<>(reactiveCache, CACHE_NAME + IDX.incrementAndGet(), maxConcurrency, meterRegistry);
    }
}
