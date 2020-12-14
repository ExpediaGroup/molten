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

package com.hotels.molten.core.collapser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import com.hotels.molten.core.MoltenCore;
import com.hotels.molten.core.mdc.MoltenMDC;
import com.hotels.molten.core.metrics.MetricId;
import com.hotels.molten.core.metrics.MoltenMetrics;
import com.hotels.molten.test.AssertSubscriber;

/**
 * Unit test for {@link RequestCollapser}.
 */
@Slf4j
public class RequestCollapserTest {
    private static final int CONTEXT = 1;
    private static final String RESULT = "result";
    private static final String HIERARCHICAL_METRICS_QUALIFIER = "metrics.hierarchical";
    private static final String METRICS_QUALIFIER = "metrics_dimensional";
    private static final String TAG_KEY = "tag-key";
    private static final String TAG_VALUE = "tag-value";
    private static final String MDC_KEY = "key";
    @Mock
    private Function<Integer, Mono<String>> valueProvider;
    private RequestCollapser<Integer, String> collapsedProvider;
    private VirtualTimeScheduler scheduler;
    private VirtualTimeScheduler timeoutScheduler;
    private MeterRegistry meterRegistry;

    @BeforeMethod
    public void initContext() {
        MDC.clear();
        MoltenCore.initialize();
        MoltenMDC.initialize();
        MockitoAnnotations.initMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        scheduler = VirtualTimeScheduler.create();
        timeoutScheduler = VirtualTimeScheduler.create();
    }

    @AfterMethod
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        MDC.clear();
        MoltenMDC.uninitialize();
    }

    @Test
    public void should_collapse_same_requests_while_first_one_is_not_emitting() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        when(valueProvider.apply(CONTEXT)).thenReturn(Mono.delay(Duration.ofMillis(50), scheduler).map(i -> RESULT));
        collapsedProvider = RequestCollapser.builder(valueProvider)
            .withScheduler(scheduler)
            .releaseWhenFinished()
            .withMetrics(meterRegistry, MetricId.builder().name(METRICS_QUALIFIER).hierarchicalName(HIERARCHICAL_METRICS_QUALIFIER).build())
            .build();
        DistributionSummary callHistogram = meterRegistry.get(HIERARCHICAL_METRICS_QUALIFIER + ".pending").summary();

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        Mono<String> first = collapsedProvider.apply(CONTEXT);
        assertThat(callHistogram.count()).isEqualTo(1);
        assertThat(callHistogram.max()).isEqualTo(1);
        assertThat(callHistogram.totalAmount()).isEqualTo(1);
        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        Mono<String> second = collapsedProvider.apply(CONTEXT);
        assertThat(callHistogram.count()).isEqualTo(2);
        assertThat(callHistogram.totalAmount()).isEqualTo(3);
        verify(valueProvider, times(1)).apply(CONTEXT);
        first.subscribe(subscriber1);
        second.subscribe(subscriber2);
        subscriber1.assertNoValues().assertNotTerminated();
        subscriber2.assertNoValues().assertNotTerminated();
        assertThat(collapsedProvider.getNumberOfOnGoingCalls()).isEqualTo(1);

        scheduler.advanceTimeBy(Duration.ofMillis(50));
        assertThat(collapsedProvider.getNumberOfOnGoingCalls()).isEqualTo(0);
        assertThat(callHistogram.count()).isEqualTo(4);
        assertThat(callHistogram.totalAmount()).isEqualTo(4);

        subscriber1.assertResult(RESULT);
        subscriber2.assertResult(RESULT);
    }

    @Test
    public void should_register_dimensional_metrics_when_enabled() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(false);
        collapsedProvider = RequestCollapser.builder(valueProvider)
            .withScheduler(scheduler)
            .releaseWhenFinished()
            .withMetrics(meterRegistry, MetricId.builder().name(METRICS_QUALIFIER).hierarchicalName(HIERARCHICAL_METRICS_QUALIFIER).tag(Tag.of(TAG_KEY, TAG_VALUE)).build())
            .build();
        // When
        when(valueProvider.apply(CONTEXT)).thenReturn(Mono.just(RESULT));
        StepVerifier.create(collapsedProvider.apply(CONTEXT)).expectNext(RESULT).verifyComplete();
        // Then
        DistributionSummary callHistogram = meterRegistry.get(METRICS_QUALIFIER + "_pending_count")
            .tag(TAG_KEY, TAG_VALUE)
            .summary();
        assertThat(callHistogram.max()).isEqualTo(1);
        assertThat(callHistogram.totalAmount()).isEqualTo(1);
        assertThat(callHistogram.count()).isEqualTo(2);
        Gauge currentCount = meterRegistry.get(METRICS_QUALIFIER + "_pending_current_count")
            .tag(TAG_KEY, TAG_VALUE)
            .gauge();
        assertThat(currentCount.value()).isEqualTo(0);
    }

    @Test
    public void should_not_start_new_request_for_same_context_until_first_one_finished() {
        when(valueProvider.apply(CONTEXT)).thenReturn(Mono.delay(Duration.ofMillis(50), scheduler).map(i -> RESULT));
        collapsedProvider = RequestCollapser.builder(valueProvider).withScheduler(scheduler).releaseWhenFinished().build();

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        Mono<String> first = collapsedProvider.apply(CONTEXT);
        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        Mono<String> second = collapsedProvider.apply(CONTEXT);

        verify(valueProvider, times(1)).apply(CONTEXT);
        first.subscribe(subscriber1);
        second.subscribe(subscriber2);
        subscriber1.assertNoValues().assertNotTerminated();
        subscriber2.assertNoValues().assertNotTerminated();

        scheduler.advanceTimeBy(Duration.ofMillis(50));
        subscriber1.assertResult(RESULT);
        subscriber2.assertResult(RESULT);

        AssertSubscriber<String> subscriber3 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT).subscribe(subscriber3);

        verify(valueProvider, times(2)).apply(CONTEXT);
        scheduler.advanceTimeBy(Duration.ofMillis(50));
        subscriber3.assertResult(RESULT);
    }

    @Test
    public void should_not_subscribe_to_source_until_subscribed_to_collapsed() {
        AtomicBoolean subscribed = new AtomicBoolean();
        when(valueProvider.apply(CONTEXT)).thenReturn(Mono.just(RESULT).doOnSubscribe(s -> subscribed.set(true)));
        collapsedProvider = RequestCollapser.builder(valueProvider).withScheduler(scheduler).build();

        Mono<String> first = collapsedProvider.apply(CONTEXT);
        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        assertThat(subscribed.get()).isFalse();
        first.subscribe(subscriber1);
        scheduler.advanceTime();
        assertThat(subscribed.get()).isTrue();
        subscriber1.assertResult(RESULT);
    }

    @Test
    public void should_propagate_error() {
        when(valueProvider.apply(CONTEXT)).thenReturn(Mono.delay(Duration.ofMillis(50), scheduler).flatMap(i -> Mono.error(new IllegalStateException("not good"))));
        collapsedProvider = RequestCollapser.builder(valueProvider).withScheduler(scheduler).build();

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT).subscribe(subscriber1);
        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT).subscribe(subscriber2);
        verify(valueProvider, times(1)).apply(CONTEXT);
        subscriber1.assertNoValues().assertNotTerminated();
        subscriber2.assertNoValues().assertNotTerminated();

        scheduler.advanceTimeBy(Duration.ofMillis(50));

        subscriber1.assertError(IllegalStateException.class);
        subscriber2.assertError(IllegalStateException.class);
    }

    @Test
    public void should_propagate_empty() {
        when(valueProvider.apply(CONTEXT)).thenReturn(Mono.delay(Duration.ofMillis(50), scheduler).flatMap(i -> Mono.empty()));
        collapsedProvider = RequestCollapser.builder(valueProvider).withScheduler(scheduler).build();

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT).subscribe(subscriber1);
        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT).subscribe(subscriber2);
        verify(valueProvider, times(1)).apply(CONTEXT);
        subscriber1.assertNoValues().assertNotTerminated();
        subscriber2.assertNoValues().assertNotTerminated();

        scheduler.advanceTimeBy(Duration.ofMillis(50));

        subscriber1.assertResult();
        subscriber2.assertResult();
    }

    @Test
    public void should_delegate_by_context() {
        when(valueProvider.apply(anyInt())).thenAnswer(ie -> Mono.delay(Duration.ofMillis(50), scheduler).map(i -> String.valueOf(ie.getArguments()[0])));
        collapsedProvider = RequestCollapser.builder(valueProvider)
            .withMaxCollapsedCalls(10)
            .withMaxCollapsedTime(Duration.ofSeconds(3))
            .withScheduler(scheduler).build();

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(1).subscribe(subscriber1);
        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(2).subscribe(subscriber2);
        assertThat(collapsedProvider.getNumberOfOnGoingCalls()).isEqualTo(2);

        scheduler.advanceTimeBy(Duration.ofMillis(50));
        subscriber1.assertResult("1");
        subscriber2.assertResult("2");
    }

    @Test
    public void should_work_with_default_scheduler() {
        when(valueProvider.apply(anyInt())).thenAnswer(
            ie -> Mono.just(1).map(i -> String.valueOf(ie.getArguments()[0])).doOnSuccess(i -> LOG.info("{}", i)));
        ConcurrentHashMap<Integer, Mono<String>> onGoingCallsStore = new ConcurrentHashMap<>();
        collapsedProvider = RequestCollapser.builder(valueProvider)
            .withOnGoingCallsStore(onGoingCallsStore).build();

        Map<String, Boolean> threadNames = new ConcurrentHashMap<>();
        AssertSubscriber<String> subscriber = AssertSubscriber.create();
        Consumer<String> storeThreadName = i -> threadNames.put(Thread.currentThread().getName(), true);
        Flux.concat(
            collapsedProvider.apply(1).doOnSuccess(i -> LOG.info("#1A {}", i)).doOnSuccess(storeThreadName),
            collapsedProvider.apply(2).doOnSuccess(i -> LOG.info("#2A {}", i)).doOnSuccess(storeThreadName),
            collapsedProvider.apply(1).doOnSuccess(i -> LOG.info("#1B {}", i)).doOnSuccess(storeThreadName)
        ).subscribe(subscriber);
        subscriber.await().assertResult("1", "2", "1");
        assertThat(threadNames)
            .hasSize(3)
            .allSatisfy((name, i) -> assertThat(name).startsWith("parallel"));
    }

    @Test
    public void should_time_out_if_collapsed_call_doesnt_complete_in_time() {
        when(valueProvider.apply(CONTEXT)).thenReturn(Mono.delay(Duration.ofMillis(100), scheduler).map(i -> RESULT));
        collapsedProvider = RequestCollapser.builder(valueProvider)
            .withScheduler(scheduler)
            .timeOutIn(Duration.ofMillis(50))
            .withTimeOutScheduler(timeoutScheduler)
            .build();

        AssertSubscriber<String> subscriber = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT).subscribe(subscriber);
        subscriber.assertNoValues().assertNotTerminated();
        timeoutScheduler.advanceTimeBy(Duration.ofMillis(50));
        subscriber.assertError(TimeoutException.class);
    }

    @Test
    public void should_maintain_expected_MDC_values() {
        //Given
        when(valueProvider.apply(CONTEXT)).thenReturn(Mono.just(RESULT).delayElement(Duration.ofMillis(50)));
        collapsedProvider = RequestCollapser.builder(valueProvider).releaseWhenFinished().build();
        //When
        MDC.put(MDC_KEY, "a");
        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT)
            .map(e -> Optional.ofNullable(MDC.get(MDC_KEY)).orElse("n/a"))
            .subscribe(subscriber1);
        MDC.put(MDC_KEY, "b");
        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT)
            .map(e -> Optional.ofNullable(MDC.get(MDC_KEY)).orElse("n/a"))
            .subscribe(subscriber2);
        //Then
        subscriber1.await();
        subscriber2.await();
        subscriber1.assertResult("a");
        subscriber2.assertResult("b");
    }
}
