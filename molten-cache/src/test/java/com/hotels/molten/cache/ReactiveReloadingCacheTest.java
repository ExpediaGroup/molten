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

import static com.hotels.molten.core.metrics.MetricsSupport.GRAPHITE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.scheduler.VirtualTimeScheduler;

import com.hotels.molten.cache.ReactiveReloadingCache.Builder;
import com.hotels.molten.cache.ReactiveReloadingCache.TimestampedValue;
import com.hotels.molten.core.metrics.MoltenMetrics;
import com.hotels.molten.test.AssertSubscriber;
import com.hotels.molten.test.TestClock;

/**
 * Unit test for {@link ReactiveReloadingCache}.
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
public class ReactiveReloadingCacheTest {
    private static final int KEY = 1;
    private static final String VALUE = "1";
    private static final String OTHER_VALUE = "2";
    private static final Duration REFRESH_TIME = Duration.ofSeconds(10);
    private static final Duration MORE_THAN_REFRESH_TIME = Duration.ofSeconds(11);
    private Map<Integer, TimestampedValue<String>> cache;
    private ReactiveCache<Integer, TimestampedValue<String>> reactiveCache;
    @Mock
    private StubService service;
    private VirtualTimeScheduler scheduler;
    private TestClock clock;
    private MeterRegistry meterRegistry;
    private Gauge loadCounter;
    private Gauge loadExceptionCounter;
    private Gauge asyncLoadCounter;
    private Gauge asyncLoadExceptionCounter;

    @BeforeEach
    public void initContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        meterRegistry = new SimpleMeterRegistry();
        cache = new ConcurrentHashMap<>();
        scheduler = VirtualTimeScheduler.create();
        reactiveCache = new DelayedReactiveMapCache<>(cache, scheduler);
        clock = TestClock.get();
    }

    @AfterEach
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(false);
    }

    @Test
    public void should_delegate_cache_gets() {
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().build();
        cache.put(KEY, new TimestampedValue<>(VALUE, clock.now()));

        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        waitForGet();
        test.assertResult(VALUE);
    }
    @Test
    public void should_delegate_cache_puts() {
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().build();

        AssertSubscriber<Void> test = AssertSubscriber.create();
        reloadingCache.put(KEY, VALUE).subscribe(test);
        waitForPut();
        test.assertResult();

        assertThat(cache).containsEntry(KEY, new TimestampedValue<>(VALUE, clock.now()));
    }

    @Test
    public void should_load_missing_entry_but_put_should_be_async() {
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().build();
        loadHierarchicalMeters();
        when(service.get(KEY)).thenReturn(Mono.just(VALUE).delayElement(Duration.ofSeconds(1), scheduler));

        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        waitForGet();
        test.assertNoValues().assertNotTerminated();
        scheduler.advanceTimeBy(Duration.ofSeconds(1)); //wait for service to complete
        test.assertResult(VALUE);
        assertThat(loadCounter.value()).isEqualTo(1);
        assertThat(asyncLoadCounter.value()).isEqualTo(0);
        assertThat(cache).isEmpty(); // put should happen async
        waitForPut(); // for delegated put
        assertThat(cache).containsEntry(KEY, new TimestampedValue<>(VALUE, clock.now()));
    }

    @Test
    public void should_register_dimensional_metrics_when_enabled() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().build();
        // When
        when(service.get(KEY)).thenReturn(Mono.just(VALUE).delayElement(Duration.ofSeconds(1), scheduler));
        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        // Then
        waitForGet();
        test.assertNoValues().assertNotTerminated();
        scheduler.advanceTimeBy(Duration.ofSeconds(1)); //wait for service to complete
        test.assertResult(VALUE);
        loadCounter = meterRegistry.get("cache_total_load_count").tag("name", "cache.stats").tag(GRAPHITE_ID, "cache.stats.total-load-count").gauge();
        asyncLoadCounter = meterRegistry.get("cache_async_load_count").tag("name", "cache.stats").tag(GRAPHITE_ID, "cache.stats.async-load-count").gauge();
        assertThat(loadCounter.value()).isEqualTo(1);
        assertThat(asyncLoadCounter.value()).isEqualTo(0);
    }

    @Test
    public void should_async_reload_cached_value_if_ttrexpired() {
        Instant initialTime = clock.now();
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().build();
        loadHierarchicalMeters();
        cache.put(KEY, new TimestampedValue<>(VALUE, clock.now()));
        when(service.get(KEY)).thenReturn(Mono.just(OTHER_VALUE));
        clock.advanceTimeBy(MORE_THAN_REFRESH_TIME); //this will make cached value TTR expired

        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        waitForGet();
        test.assertResult(VALUE); // this will return the cached value but will trigger a reload
        assertThat(loadCounter.value()).isEqualTo(1);
        assertThat(asyncLoadCounter.value()).isEqualTo(1);
        assertThat(cache).containsEntry(KEY, new TimestampedValue<>(VALUE, initialTime));
        waitForPut(); //for delegated put

        AssertSubscriber<String> test2 = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test2);
        waitForGet();
        test2.assertResult(OTHER_VALUE);
        assertThat(cache).containsEntry(KEY, new TimestampedValue<>(OTHER_VALUE, clock.now()));
    }

    @Test
    public void should_cache_empty_result() {
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().build();
        loadHierarchicalMeters();
        when(service.get(KEY)).thenReturn(Mono.empty());

        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        waitForGet();
        test.assertResult();
        assertThat(loadCounter.value()).isEqualTo(1);
        waitForPut(); // for delegated put
        assertThat(cache).containsEntry(KEY, new TimestampedValue<>(null, clock.now()));
        AssertSubscriber<String> test2 = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test2);
        waitForGet();
        test2.assertResult();
        verify(service, times(1)).get(KEY);
    }

    @Test
    public void should_reload_empty_result() {
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().build();
        loadHierarchicalMeters();
        cache.put(KEY, new TimestampedValue<>(VALUE, clock.now()));
        when(service.get(KEY)).thenReturn(Mono.empty());
        clock.advanceTimeBy(MORE_THAN_REFRESH_TIME);

        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        waitForGet();
        test.assertResult(VALUE);
        assertThat(loadCounter.value()).isEqualTo(1);
        assertThat(asyncLoadCounter.value()).isEqualTo(1);
        waitForPut();
        assertThat(cache).containsEntry(KEY, new TimestampedValue<>(null, clock.now()));
        AssertSubscriber<String> test2 = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test2);
        waitForGet();
        test2.assertResult();
        verify(service, times(1)).get(KEY);
    }

    @Test
    public void should_emit_loading_error_and_not_cache_it_if_not_fail_safe() {
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().build();
        loadHierarchicalMeters();
        when(service.get(KEY)).thenReturn(Mono.error(new IllegalStateException()));

        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        waitForGet();
        test.assertError(IllegalStateException.class);
        assertThat(loadCounter.value()).isEqualTo(1);
        assertThat(loadExceptionCounter.value()).isEqualTo(1);
        assertThat(asyncLoadCounter.value()).isEqualTo(0);
        assertThat(asyncLoadExceptionCounter.value()).isEqualTo(0);
        assertThat(cache).isEmpty();
    }

    @Test
    public void should_return_empty_if_fail_safe_and_loading_error_happened() {
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().failSafeLoading().build();
        loadHierarchicalMeters();
        when(service.get(KEY)).thenReturn(Mono.error(new IllegalStateException()));

        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        waitForGet();
        test.assertResult();
        assertThat(loadExceptionCounter.value()).isEqualTo(1);
        assertThat(cache).isEmpty();
    }

    @Test
    public void should_keep_previous_value_if_reloading_error_happened() {
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().build();
        loadHierarchicalMeters();
        Instant initialTime = clock.now();
        cache.put(KEY, new TimestampedValue<>(VALUE, initialTime));
        when(service.get(KEY)).thenReturn(Mono.error(new IllegalStateException()));
        clock.advanceTimeBy(MORE_THAN_REFRESH_TIME);

        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        waitForGet();
        test.assertResult(VALUE);
        waitForPut(); // for delegated put just in case but it shouldn't be called
        assertThat(cache).containsEntry(KEY, new TimestampedValue<>(VALUE, initialTime));
        verify(service, times(1)).get(KEY);
    }

    @Test
    public void should_keep_previous_value_if_reloading_error_happened_even_if_fail_safe() {
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().failSafeLoading().build();
        loadHierarchicalMeters();
        Instant initialTime = clock.now();
        cache.put(KEY, new TimestampedValue<>(VALUE, initialTime));
        when(service.get(KEY)).thenReturn(Mono.error(new IllegalStateException()));
        clock.advanceTimeBy(MORE_THAN_REFRESH_TIME);

        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        waitForGet();
        test.assertResult(VALUE);
        waitForPut(); // for delegated put just in case but it shouldn't be called
        assertThat(cache).containsEntry(KEY, new TimestampedValue<>(VALUE, initialTime));
    }

    @Test
    public void should_emit_cache_get_error() {
        ReactiveCache<Integer, TimestampedValue<String>> failingCache = mock(ReactiveCache.class);
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder(failingCache).build();
        loadHierarchicalMeters();
        when(failingCache.get(KEY)).thenReturn(Mono.error(new IllegalStateException()));

        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        test.assertError(IllegalStateException.class);
        assertThat(cache).isEmpty();
        assertThat(loadExceptionCounter.value()).isEqualTo(0);
    }

    @Test
    public void should_emit_cache_put_error() {
        ReactiveCache<Integer, TimestampedValue<String>> failingCache = mock(ReactiveCache.class);
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder(failingCache).build();
        loadHierarchicalMeters();
        when(failingCache.put(KEY, new TimestampedValue<>(VALUE, clock.now()))).thenReturn(Mono.error(new IllegalStateException()));

        AssertSubscriber<Void> test = AssertSubscriber.create();
        reloadingCache.put(KEY, VALUE).subscribe(test);
        test.assertError(IllegalStateException.class);
        assertThat(cache).isEmpty();
        assertThat(loadExceptionCounter.value()).isEqualTo(0);
    }

    @Test
    public void should_call_service_for_cache_get_error_if_fallback_is_enabled() {
        ReactiveCache<Integer, TimestampedValue<String>> failingCache = mock(ReactiveCache.class);
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder(failingCache).fallbackLoading().build();
        loadHierarchicalMeters();
        when(failingCache.get(KEY)).thenReturn(Mono.error(new IllegalStateException()));
        when(failingCache.put(anyInt(), any(TimestampedValue.class))).thenReturn(Mono.empty());
        when(service.get(KEY)).thenReturn(Mono.just(VALUE));

        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        scheduler.advanceTime();
        test.assertResult(VALUE);
        verify(failingCache, times(1)).put(KEY, new TimestampedValue<>(VALUE, clock.now()));
        assertThat(loadCounter.value()).isEqualTo(1);
        assertThat(loadExceptionCounter.value()).isEqualTo(0);
    }

    @Test
    public void should_convert_context_and_value_if_needed() {
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().loadingWith(service::get, c -> Mono.just(c + 1), v -> Mono.just(v + "!"),
            cv -> Mono.just(cv.substring(0, 1))).build();
        loadHierarchicalMeters();
        AssertSubscriber<Void> test = AssertSubscriber.create();
        reloadingCache.put(KEY, VALUE).subscribe(test);
        waitForPut();
        test.assertResult();
        assertThat(cache).containsEntry(KEY + 1, new TimestampedValue<>(VALUE + "!", clock.now()));

        AssertSubscriber<String> test1 = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test1);
        waitForGet();
        test1.assertResult(VALUE);
    }

    @Test
    public void should_convert_context_and_value_type_if_needed() {
        ReactiveCache<Long, Boolean> reloadingCache = ReactiveReloadingCache.over(new ReactiveMapCache<>(cache))
            .loadingWith(service::otherGet, c -> Mono.just(c.intValue()), c -> Mono.just(Boolean.toString(c)), v -> Mono.just(Boolean.valueOf(v)))
            .withTimeToRefresh(REFRESH_TIME)
            .withClock(clock)
            .withScheduler(scheduler)
            .withMetrics(meterRegistry, "cache.stats")
            .build();
        loadHierarchicalMeters();
        AssertSubscriber<Void> test = AssertSubscriber.create();
        reloadingCache.put(1L, Boolean.TRUE).subscribe(test);
        waitForPut();
        test.assertResult();
        assertThat(cache).containsEntry(1, new TimestampedValue<>("true", clock.now()));

        AssertSubscriber<Boolean> test1 = AssertSubscriber.create();
        reloadingCache.get(1L).subscribe(test1);
        waitForGet();
        test1.assertResult(Boolean.TRUE);
    }

    @Test
    public void should_return_empty_for_initial_get_if_not_cached_and_trigger_async_loading() {
        ReactiveCache<Integer, String> reloadingCache = reloadingCacheBuilder().emptyIfNotCached().build();
        loadHierarchicalMeters();
        when(service.get(KEY)).thenReturn(Mono.just(VALUE));

        AssertSubscriber<String> test = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test);
        assertThat(loadCounter.value()).isEqualTo(0);
        assertThat(asyncLoadCounter.value()).isEqualTo(0);
        waitForGet();
        assertThat(asyncLoadCounter.value()).isEqualTo(1);
        test.assertResult(); // this will return the empty value but will trigger a load
        assertThat(cache).isEmpty();
        waitForPut(); //for delegated put
        assertThat(loadCounter.value()).isEqualTo(1);

        AssertSubscriber<String> test2 = AssertSubscriber.create();
        reloadingCache.get(KEY).subscribe(test2);
        waitForGet();
        test2.assertResult(VALUE);
        assertThat(cache).containsEntry(KEY, new TimestampedValue<>(VALUE, clock.now()));
    }

    private void waitForPut() {
        scheduler.advanceTimeBy(DelayedReactiveMapCache.PUT_DELAY);
    }

    private void waitForGet() {
        scheduler.advanceTimeBy(DelayedReactiveMapCache.GET_DELAY);
    }

    private Builder<Integer, String, Integer, String> reloadingCacheBuilder() {
        return reloadingCacheBuilder(reactiveCache);
    }

    private Builder<Integer, String, Integer, String> reloadingCacheBuilder(ReactiveCache<Integer, TimestampedValue<String>> cache) {
        return ReactiveReloadingCache.over(cache)
            .loadingWith(service::get)
            .withTimeToRefresh(REFRESH_TIME)
            .withClock(clock)
            .withScheduler(scheduler)
            .withMetrics(meterRegistry, "cache.stats");
    }

    private void loadHierarchicalMeters() {
        loadCounter = meterRegistry.get("cache.stats.total-load-count").gauge();
        loadExceptionCounter = meterRegistry.get("cache.stats.total-load-exception-count").gauge();
        asyncLoadCounter = meterRegistry.get("cache.stats.async-load-count").gauge();
        asyncLoadExceptionCounter = meterRegistry.get("cache.stats.async-load-exception-count").gauge();
    }

    private interface StubService {
        Mono<String> get(Integer ctx);

        Mono<Boolean> otherGet(Long ctx);
    }
}
