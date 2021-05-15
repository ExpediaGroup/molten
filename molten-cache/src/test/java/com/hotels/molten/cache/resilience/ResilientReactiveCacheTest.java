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

import static com.hotels.molten.core.metrics.MetricsSupport.GRAPHITE_ID;
import static com.hotels.molten.core.metrics.MetricsSupport.name;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.cache.ReactiveCacheTestContract;
import com.hotels.molten.cache.ReactiveMapCache;
import com.hotels.molten.core.metrics.MoltenMetrics;
import com.hotels.molten.test.AssertSubscriber;

/**
 * Unit test for {@link ResilientReactiveCache}.
 */
@ExtendWith(MockitoExtension.class)
public class ResilientReactiveCacheTest implements ReactiveCacheTestContract {
    private static final Long KEY = 1L;
    private static final String VALUE = "value";
    private static final String CACHE_NAME = "cacheName";
    private static final AtomicInteger IDX = new AtomicInteger();
    @Mock
    private ReactiveCache<Long, String> cache;
    private MeterRegistry meterRegistry;
    private VirtualTimeScheduler scheduler;
    private String cacheName;

    @Override
    public <T> ReactiveCache<Integer, T> createCacheForContractTest() {
        return getResilientCache(new ReactiveMapCache<>(new ConcurrentHashMap<>()), CircuitBreakerConfig.ofDefaults());
    }

    @BeforeEach
    public void initContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        meterRegistry = new SimpleMeterRegistry();
        scheduler = VirtualTimeScheduler.create();
        VirtualTimeScheduler.set(scheduler);
        cacheName = nextCacheName();
    }

    @AfterEach
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        VirtualTimeScheduler.reset();
    }

    @Test
    public void should_delegate_get_call() {
        when(cache.get(KEY)).thenReturn(Mono.just(VALUE));

        StepVerifier.create(getResilientCache(cache, CircuitBreakerConfig.ofDefaults()).get(KEY))
            .expectNext(VALUE)
            .verifyComplete();
    }

    @Test
    public void should_timeout_get_call() {
        when(cache.get(KEY)).thenReturn(Mono.defer(() -> Mono.delay(Duration.ofMillis(15))).map(i -> VALUE));

        AssertSubscriber<String> test = AssertSubscriber.create();
        getResilientCache(cache, CircuitBreakerConfig.ofDefaults()).get(KEY).subscribe(test);
        scheduler.advanceTimeBy(Duration.ofMillis(20));
        test.assertError(TimeoutException.class);
        assertThat(meterRegistry.get(name("reactive-cache", cacheName, "get", "timeout")).gauge().value()).isEqualTo(1);
    }

    @Test
    public void should_not_timeout_get_call_with_put_only_timeout() {
        when(cache.get(KEY)).thenReturn(Mono.defer(() -> Mono.delay(Duration.ofMillis(5))).map(i -> VALUE));

        new ResilientReactiveCache<>(cache, cacheName, Duration.ofMillis(10), Duration.ofMillis(2), CircuitBreakerConfig.ofDefaults(), meterRegistry)
            .get(KEY)
            .as(StepVerifier::create)
            .expectSubscription()
            .then(() -> scheduler.advanceTimeBy(Duration.ofMillis(10)))
            .expectNext(VALUE)
            .verifyComplete();
        assertThat(meterRegistry.get(name("reactive-cache", cacheName, "get", "timeout")).gauge().value()).isEqualTo(0);
    }

    @Test
    public void should_register_dimensional_metric_for_timeout() {
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        when(cache.get(KEY)).thenReturn(Mono.defer(() -> Mono.delay(Duration.ofMillis(15))).map(i -> VALUE));

        AssertSubscriber<String> test = AssertSubscriber.create();
        getResilientCache(cache, CircuitBreakerConfig.ofDefaults()).get(KEY).subscribe(test);
        scheduler.advanceTimeBy(Duration.ofMillis(20));
        test.assertError(TimeoutException.class);
        assertThat(meterRegistry.get("cache_request_timeouts")
            .tag("name", cacheName)
            .tag("operation", "get")
            .tag(GRAPHITE_ID, name("reactive-cache", cacheName, "get", "timeout"))
            .gauge().value()).isEqualTo(1);
    }

    @Test
    public void should_break_circuit_of_get_call() {
        AtomicInteger subscriptionCount = new AtomicInteger();
        when(cache.get(KEY)).thenReturn(Mono.create(e -> {
            subscriptionCount.incrementAndGet();
            e.error(new IllegalStateException());
        }));

        var circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(0.5F)
            .slidingWindow(2, 2, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .permittedNumberOfCallsInHalfOpenState(2)
            .build();
        ResilientReactiveCache<Long, String> resilientCache = getResilientCache(cache, circuitBreakerConfig);

        AssertSubscriber<String> test = AssertSubscriber.create();
        resilientCache.get(KEY).subscribe(test);
        test.assertError(IllegalStateException.class);
        test = AssertSubscriber.create();
        resilientCache.get(KEY).subscribe(test);
        test.assertError(IllegalStateException.class);
        test = AssertSubscriber.create();
        resilientCache.get(KEY).subscribe(test);
        test.assertError(CallNotPermittedException.class);
        test = AssertSubscriber.create();
        resilientCache.get(KEY).subscribe(test);
        test.assertError(CallNotPermittedException.class);

        assertThat(subscriptionCount.get()).isEqualTo(2);
    }

    @Test
    public void should_delegate_put_call() {
        when(cache.put(KEY, VALUE)).thenReturn(Mono.empty());

        StepVerifier.create(getResilientCache(cache, CircuitBreakerConfig.ofDefaults()).put(KEY, VALUE)).verifyComplete();

        verify(cache).put(KEY, VALUE);
    }

    @Test
    public void should_timeout_put_call() {
        when(cache.put(KEY, VALUE)).thenReturn(Mono.defer(() -> Mono.delay(Duration.ofMillis(15))).then());

        AssertSubscriber<Void> test = AssertSubscriber.create();
        getResilientCache(cache, CircuitBreakerConfig.ofDefaults()).put(KEY, VALUE).subscribe(test);
        scheduler.advanceTimeBy(Duration.ofMillis(20));
        test.assertError(TimeoutException.class);
        assertThat(meterRegistry.get(name("reactive-cache", cacheName, "put", "timeout")).gauge().value()).isEqualTo(1);
    }

    @Test
    public void should_timeout_put_call_with_put_only_timeout() {
        when(cache.put(KEY, VALUE)).thenReturn(Mono.defer(() -> Mono.delay(Duration.ofMillis(25))).then());

        AssertSubscriber<Void> test = AssertSubscriber.create();
        new ResilientReactiveCache<>(cache, cacheName, Duration.ofMillis(10), Duration.ofMillis(20), CircuitBreakerConfig.ofDefaults(), meterRegistry)
            .put(KEY, VALUE)
            .subscribe(test);
        scheduler.advanceTimeBy(Duration.ofMillis(30));
        test.assertError(TimeoutException.class);
        assertThat(meterRegistry.get(name("reactive-cache", cacheName, "put", "timeout")).gauge().value()).isEqualTo(1);
    }

    @Test
    public void should_not_timeout_put_call_with_longer_call_then_get_only_timeout() {
        when(cache.put(KEY, VALUE)).thenReturn(Mono.defer(() -> Mono.delay(Duration.ofMillis(15))).then());

        new ResilientReactiveCache<>(cache, cacheName, Duration.ofMillis(10), Duration.ofMillis(20), CircuitBreakerConfig.ofDefaults(), meterRegistry)
            .put(KEY, VALUE)
            .as(StepVerifier::create)
            .expectSubscription()
            .then(() -> scheduler.advanceTimeBy(Duration.ofMillis(20)))
            .verifyComplete();
        verify(cache).put(KEY, VALUE);
        assertThat(meterRegistry.get(name("reactive-cache", cacheName, "put", "timeout")).gauge().value()).isEqualTo(0);
    }

    @Test
    public void should_break_circuit_of_put_call() {
        AtomicInteger subscriptionCount = new AtomicInteger();
        when(cache.put(KEY, VALUE)).thenReturn(Mono.create(e -> {
            subscriptionCount.incrementAndGet();
            e.error(new IllegalStateException());
        }));
        var circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(0.5F)
            .slidingWindow(2, 2, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .permittedNumberOfCallsInHalfOpenState(2)
            .build();
        ResilientReactiveCache<Long, String> resilientCache = getResilientCache(cache, circuitBreakerConfig);

        AssertSubscriber<Void> test = AssertSubscriber.create();
        resilientCache.put(KEY, VALUE).subscribe(test);
        test.assertError(IllegalStateException.class);
        test = AssertSubscriber.create();
        resilientCache.put(KEY, VALUE).subscribe(test);
        test.assertError(IllegalStateException.class);
        test = AssertSubscriber.create();
        resilientCache.put(KEY, VALUE).subscribe(test);
        test.assertError(CallNotPermittedException.class);
        test = AssertSubscriber.create();
        resilientCache.put(KEY, VALUE).subscribe(test);
        test.assertError(CallNotPermittedException.class);

        assertThat(subscriptionCount.get()).isEqualTo(2);
    }

    @Test
    public void should_have_common_circuit_for_get_and_put() {
        when(cache.put(KEY, VALUE)).thenReturn(Mono.error(new IllegalStateException()));
        when(cache.get(KEY)).thenReturn(Mono.just(VALUE));
        var circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(0.5F)
            .slidingWindow(2, 2, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .permittedNumberOfCallsInHalfOpenState(2)
            .build();
        ResilientReactiveCache<Long, String> resilientCache = getResilientCache(cache, circuitBreakerConfig);

        AssertSubscriber<Void> test = AssertSubscriber.create();
        resilientCache.put(KEY, VALUE).subscribe(test);
        test.assertError(IllegalStateException.class);
        test = AssertSubscriber.create();
        resilientCache.put(KEY, VALUE).subscribe(test);
        test.assertError(IllegalStateException.class);
        test = AssertSubscriber.create();
        resilientCache.put(KEY, VALUE).subscribe(test);
        test.assertError(CallNotPermittedException.class);
        AssertSubscriber<String> test2 = AssertSubscriber.create();
        resilientCache.get(KEY).subscribe(test2);
        test.assertError(CallNotPermittedException.class);
        assertThat(meterRegistry.get("reactive-cache." + cacheName + ".circuit." + "successful").gauge().value()).isEqualTo(0D);
        assertThat(meterRegistry.get("reactive-cache." + cacheName + ".circuit." + "failed").gauge().value()).isEqualTo(2D);
        assertThat(meterRegistry.get("reactive-cache." + cacheName + ".circuit." + "rejected").gauge().value()).isEqualTo(2D);
    }

    @Test
    public void should_not_reuse_cache_name() {
        getResilientCache(cache, CircuitBreakerConfig.ofDefaults());
        assertThatThrownBy(() -> getResilientCache(cache, CircuitBreakerConfig.ofDefaults()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The cache name=" + cacheName + " cannot be reused.");
    }

    private String nextCacheName() {
        return CACHE_NAME + IDX.incrementAndGet();
    }

    private <K, V> ResilientReactiveCache<K, V> getResilientCache(ReactiveCache<K, V> reactiveCache, CircuitBreakerConfig circuitBreakerConfig) {
        return new ResilientReactiveCache<>(reactiveCache, cacheName, Duration.ofMillis(10), circuitBreakerConfig, meterRegistry);
    }
}
