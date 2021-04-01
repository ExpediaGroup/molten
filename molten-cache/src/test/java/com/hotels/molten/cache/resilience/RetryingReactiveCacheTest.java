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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

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
import reactor.util.retry.Retry;

import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.cache.ReactiveCacheTestContract;
import com.hotels.molten.cache.ReactiveMapCache;
import com.hotels.molten.core.metrics.MoltenMetrics;
import com.hotels.molten.test.UnstableMono;

/**
 * Unit test for {@link RetryingReactiveCache}.
 */
@ExtendWith(MockitoExtension.class)
public class RetryingReactiveCacheTest implements ReactiveCacheTestContract {
    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String CACHE_NAME = "cacheName";
    private static final Duration RETRY_DELAY = Duration.ofMillis(50);
    private RetryingReactiveCache<String, String> retryingReactiveCache;
    @Mock
    private ReactiveCache<String, String> cache;
    private MeterRegistry meterRegistry;
    private VirtualTimeScheduler scheduler;

    @Override
    public <T> ReactiveCache<Integer, T> createCacheForContractTest() {
        return new RetryingReactiveCache<>(new ReactiveMapCache<>(new ConcurrentHashMap<>()), Retry.fixedDelay(2, RETRY_DELAY), meterRegistry, CACHE_NAME);
    }

    @BeforeEach
    public void initContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        meterRegistry = new SimpleMeterRegistry();
        retryingReactiveCache = new RetryingReactiveCache<>(cache, Retry.fixedDelay(2, RETRY_DELAY), meterRegistry, CACHE_NAME);
    }

    @AfterEach
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        VirtualTimeScheduler.reset();
    }

    @Test
    public void should_delegate_get_call() {
        when(cache.get(KEY)).thenReturn(Mono.just(VALUE));

        StepVerifier.create(retryingReactiveCache.get(KEY)).expectNext(VALUE).verifyComplete();

        verify(cache).get(KEY);
    }

    @Test
    public void should_retry_failed_get_call() {
        UnstableMono<String> unstableMono = UnstableMono.<String>builder().thenError(new IllegalStateException()).thenElement(VALUE).create();
        when(cache.get(KEY)).thenReturn(unstableMono.mono());

        StepVerifier.withVirtualTime(() -> retryingReactiveCache.get(KEY))
            .expectSubscription()
            .expectNoEvent(RETRY_DELAY)
            .expectNext(VALUE)
            .verifyComplete();
        assertThat(unstableMono.getSubscriptionCount()).isEqualTo(2);
        assertThat(meterRegistry.get(name("reactive-cache", CACHE_NAME, "get", "retries", "retry1")).counter().count()).isEqualTo(1D);
    }

    @Test
    public void should_register_dimensional_metrics_when_enabled() {
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        UnstableMono<String> unstableMono = UnstableMono.<String>builder().thenError(new IllegalStateException()).thenElement(VALUE).create();
        when(cache.get(KEY)).thenReturn(unstableMono.mono());

        StepVerifier.withVirtualTime(() -> retryingReactiveCache.get(KEY))
            .expectSubscription()
            .expectNoEvent(RETRY_DELAY)
            .expectNext(VALUE)
            .verifyComplete();
        assertThat(unstableMono.getSubscriptionCount()).isEqualTo(2);
        assertThat(meterRegistry.get("cache_request_retries")
            .tag("name", CACHE_NAME)
            .tag("operation", "get")
            .tag("retry", "retry1")
            .tag(GRAPHITE_ID, name("reactive-cache", CACHE_NAME, "get", "retries", "retry1"))
            .counter().count())
            .isEqualTo(1D);
    }

    @Test
    public void should_give_up_failed_get_calls_after_max_retries_and_propagate_last_error() {
        when(cache.get(KEY)).thenReturn(UnstableMono.<String>builder()
            .thenError(new IllegalStateException())
            .thenError(new IllegalStateException())
            .thenError(new IllegalArgumentException())
            .create().mono());

        StepVerifier.withVirtualTime(() -> retryingReactiveCache.get(KEY))
            .expectSubscription()
            .expectNoEvent(RETRY_DELAY.multipliedBy(2L))
            .verifyError(IllegalArgumentException.class);
    }

    @Test
    public void should_not_retry_get_before_delay() {
        UnstableMono<String> unstableMono = UnstableMono.<String>builder().thenError(new IllegalStateException()).thenElement(VALUE).create();
        when(cache.get(KEY)).thenReturn(unstableMono.mono());

        StepVerifier.withVirtualTime(() -> retryingReactiveCache.get(KEY))
            .expectSubscription()
            .expectNoEvent(RETRY_DELAY.minusMillis(10))
            .then(() -> assertThat(unstableMono.getSubscriptionCount()).isEqualTo(1))
            .expectNoEvent(Duration.ofMillis(10))
            .then(() -> assertThat(unstableMono.getSubscriptionCount()).isEqualTo(2))
            .expectNext(VALUE)
            .verifyComplete();
    }

    @Test
    public void should_retry_get_immediately_if_no_delay() {
        retryingReactiveCache = new RetryingReactiveCache<>(cache, Retry.fixedDelay(2, Duration.ZERO), meterRegistry, CACHE_NAME);
        UnstableMono<String> unstableMono = UnstableMono.<String>builder().thenError(new IllegalStateException()).thenElement(VALUE).create();
        when(cache.get(KEY)).thenReturn(unstableMono.mono());

        StepVerifier.create(retryingReactiveCache.get(KEY))
            .expectNext(VALUE)
            .then(() -> assertThat(unstableMono.getSubscriptionCount()).isEqualTo(2))
            .verifyComplete();
    }

    @Test
    public void should_delegate_put_call() {
        when(cache.put(KEY, VALUE)).thenReturn(Mono.empty());

        StepVerifier.create(retryingReactiveCache.put(KEY, VALUE)).verifyComplete();

        verify(cache).put(KEY, VALUE);
    }

    @Test
    public void should_retry_failed_put_call() {
        UnstableMono<Void> unstableMono = UnstableMono.<Void>builder().thenError(new IllegalStateException()).thenEmpty().create();
        when(cache.put(KEY, VALUE)).thenReturn(unstableMono.mono());


        StepVerifier.withVirtualTime(() -> retryingReactiveCache.put(KEY, VALUE))
            .expectSubscription()
            .expectNoEvent(RETRY_DELAY)
            .verifyComplete();
        assertThat(unstableMono.getSubscriptionCount()).isEqualTo(2);
    }

    @Test
    public void should_give_up_failed_put_calls_after_max_retries_and_propagate_last_error() {
        when(cache.put(KEY, VALUE)).thenReturn(UnstableMono.<Void>builder()
            .thenError(new IllegalStateException())
            .thenError(new IllegalStateException())
            .thenError(new IllegalArgumentException())
            .create().mono());

        StepVerifier.withVirtualTime(() -> retryingReactiveCache.put(KEY, VALUE))
            .expectSubscription()
            .expectNoEvent(RETRY_DELAY.multipliedBy(2L))
            .verifyError(IllegalArgumentException.class);
    }

    @Test
    public void should_not_retry_put_before_delay() {
        UnstableMono<Void> unstableMono = UnstableMono.<Void>builder().thenError(new IllegalStateException()).thenEmpty().create();
        when(cache.put(KEY, VALUE)).thenReturn(unstableMono.mono());

        StepVerifier.withVirtualTime(() -> retryingReactiveCache.put(KEY, VALUE))
            .expectSubscription()
            .expectNoEvent(RETRY_DELAY.minusMillis(10))
            .then(() -> assertThat(unstableMono.getSubscriptionCount()).isEqualTo(1))
            .expectNoEvent(Duration.ofMillis(10))
            .then(() -> assertThat(unstableMono.getSubscriptionCount()).isEqualTo(2))
            .verifyComplete();
    }

    @Test
    public void should_retry_put_immediately_if_no_delay() {
        retryingReactiveCache = new RetryingReactiveCache<>(cache, Retry.fixedDelay(2, Duration.ZERO), meterRegistry, CACHE_NAME);
        UnstableMono<Void> unstableMono = UnstableMono.<Void>builder().thenError(new IllegalStateException()).thenEmpty().create();
        when(cache.put(KEY, VALUE)).thenReturn(unstableMono.mono());

        StepVerifier.create(retryingReactiveCache.put(KEY, VALUE))
            .expectSubscription()
            .then(() -> assertThat(unstableMono.getSubscriptionCount()).isEqualTo(2))
            .verifyComplete();
    }
}
