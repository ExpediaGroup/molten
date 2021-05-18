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

import static com.hotels.molten.core.metrics.MetricsSupport.name;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.shouldHaveThrown;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import com.hotels.molten.core.MoltenCore;
import com.hotels.molten.core.mdc.MoltenMDC;
import com.hotels.molten.core.metrics.MetricId;
import com.hotels.molten.core.metrics.MoltenMetrics;
import com.hotels.molten.test.AssertSubscriber;

/**
 * Unit test for {@link FanOutRequestCollapser}.
 */
@SuppressWarnings("unchecked")
@Slf4j
@ExtendWith(MockitoExtension.class)
public class FanOutRequestCollapserTest {
    private static final int CONTEXT_A = 1;
    private static final String RESULT_A = "1";
    private static final int CONTEXT_B = 2;
    private static final String RESULT_B = "2";
    private static final int CONTEXT_C = 3;
    private static final String RESULT_C = "3";
    private static final String HIERARCHICAL_METRICS_QUALIFIER = "metrics.hierarchical";
    private static final String METRICS_QUALIFIER = "metrics_dimensional";
    private static final String TAG_KEY = "tag-key";
    private static final String TAG_VALUE = "tag-value";
    private static final String MDC_KEY = "key";
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private Function<List<Integer>, Mono<List<String>>> bulkProvider;
    @Mock
    private Function<List<Integer>, Flux<String>> eagerBulkProvider;
    private FanOutRequestCollapser<Integer, String> collapsedProvider;
    private VirtualTimeScheduler scheduler;
    private VirtualTimeScheduler batchScheduler;
    private VirtualTimeScheduler delayScheduler;
    private VirtualTimeScheduler emitScheduler;
    private SimpleMeterRegistry meterRegistry;
    private Timer delayTimer;
    private Timer completionTimer;
    private DistributionSummary pendingHistogram;
    private DistributionSummary batchSizeHistogram;
    private MockClock clock;

    @BeforeEach
    void initContext() {
        MoltenCore.initialize();
        MoltenMDC.initialize();
        MDC.clear();
        clock = new MockClock();
        meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);

        scheduler = VirtualTimeScheduler.create();
        batchScheduler = VirtualTimeScheduler.create();
        delayScheduler = VirtualTimeScheduler.create();
        emitScheduler = VirtualTimeScheduler.create();
    }

    private FanOutRequestCollapser.Builder<Integer, String> withBaseConfig(FanOutRequestCollapser.Builder<Integer, String> collapserBuilder) {
        return collapserBuilder
            .withContextValueMatcher((context, value) -> context.equals(Integer.parseInt(value)))
            .withScheduler(scheduler)
            .withEmitScheduler(emitScheduler)
            .withMaximumWaitTime(Duration.ofMillis(100))
            .withBatchSize(2)
            .withBatchScheduler(batchScheduler)
            .withMetrics(meterRegistry, MetricId.builder().name(METRICS_QUALIFIER).hierarchicalName(HIERARCHICAL_METRICS_QUALIFIER).tag(Tag.of(TAG_KEY, TAG_VALUE)).build());
    }

    @AfterEach
    void clearContext() {
        collapsedProvider.cancel();
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        MDC.clear();
    }

    @Test
    void should_collapse_requests_to_batches() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider)).build();
        when(bulkProvider.apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B))))
            .thenAnswer(ie -> {
                LOG.info("bulk with {}", ie.getArguments());
                return Mono.delay(Duration.ofMillis(50), delayScheduler).map(i -> List.of(RESULT_B, RESULT_A));
            });

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A).subscribe(subscriber1);
        verify(bulkProvider, never()).apply(anyList());

        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_B).subscribe(subscriber2);
        scheduler.advanceTime(); //though Reactor executes tasks without delay immediately
        batchScheduler.advanceTime();
        verify(bulkProvider, times(1)).apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B)));

        subscriber1.assertNoValues().assertNotTerminated();
        subscriber2.assertNoValues().assertNotTerminated();

        delayScheduler.advanceTimeBy(Duration.ofMillis(50));
        emitScheduler.advanceTime();
        subscriber1.assertResult(RESULT_A);
        subscriber2.assertResult(RESULT_B);
    }

    @Test
    void should_handle_hierarchical_metrics() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider)).build();
        pendingHistogram = hierarchicalMetric("item.pending").summary();
        batchSizeHistogram = hierarchicalMetric("batch.size").summary();
        delayTimer = hierarchicalMetric("item.delay").timer();
        completionTimer = hierarchicalMetric("item.completion").timer();
        when(bulkProvider.apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B))))
            .thenReturn(Mono.delay(Duration.ofMillis(50), delayScheduler).map(i -> List.of(RESULT_B, RESULT_A)));

        assertThat(pendingHistogram.count()).isEqualTo(0);
        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A).subscribe(subscriber1);
        assertThat(pendingHistogram.count()).isEqualTo(1);
        assertThat(pendingHistogram.totalAmount()).isEqualTo(1);

        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_B).subscribe(subscriber2);
        //FIXME: we should have better control over schedulers to be able to step by step verify things
        //assertThat(pendingHistogram.count()).isEqualTo(2);
        //assertThat(pendingHistogram.totalAmount()).isEqualTo(3); // 1 + 2
        //FIXME: this is the point where we should subscribe first measuring delay from here

        scheduler.advanceTime();
        //batch limit reached, emitting batch
        batchScheduler.advanceTime();
        emitScheduler.advanceTime();
        //batch limit reached, emitting batch
        assertThat(pendingHistogram.count()).isEqualTo(3);
        assertThat(pendingHistogram.totalAmount()).isEqualTo(3); // 1 + 2 + 0

        AssertSubscriber<String> subscriber3 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_C).subscribe(subscriber3);
        assertThat(pendingHistogram.count()).isEqualTo(4);
        assertThat(pendingHistogram.totalAmount()).isEqualTo(4); // 1 + 2 + 0 + 1

        clock.add(Duration.ofNanos(100));
        verify(bulkProvider, times(1)).apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B)));
        assertThat(completionTimer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(0);
        //assertThat(delayTimer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(100);

        subscriber1.assertNoValues().assertNotTerminated();
        subscriber2.assertNoValues().assertNotTerminated();

        clock.add(Duration.ofNanos(100));
        delayScheduler.advanceTimeBy(Duration.ofMillis(50));
        emitScheduler.advanceTime();
        subscriber1.assertResult(RESULT_A);
        subscriber2.assertResult(RESULT_B);
        assertThat(completionTimer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(400); // 200 + 200
        //FIXME: needs better scheduler control
        //assertThat(delayTimer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(100);
    }

    private RequiredSearch hierarchicalMetric(String name) {
        return meterRegistry.get(name(HIERARCHICAL_METRICS_QUALIFIER, name));
    }

    @Test
    void should_handle_dimensional_metrics() {
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(false);
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider)).build();
        pendingHistogram = dimensionalMetric("pending").summary();
        batchSizeHistogram = dimensionalMetric("batch_size").summary();
        delayTimer = dimensionalMetric("item_delay").timer();
        completionTimer = dimensionalMetric("item_completion").timer();
        when(bulkProvider.apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B))))
            .thenReturn(Mono.delay(Duration.ofMillis(50), delayScheduler).map(i -> List.of(RESULT_B, RESULT_A)));

        assertThat(pendingHistogram.count()).isEqualTo(0);
        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A).subscribe(subscriber1);
        assertThat(pendingHistogram.count()).isEqualTo(1);
        assertThat(pendingHistogram.totalAmount()).isEqualTo(1);

        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_B).subscribe(subscriber2);
        //FIXME: we should have better control over schedulers to be able to step by step verify things
        //assertThat(pendingHistogram.count()).isEqualTo(2);
        //assertThat(pendingHistogram.totalAmount()).isEqualTo(3); // 1 + 2
        //FIXME: this is the point where we should subscribe first measuring delay from here

        scheduler.advanceTime();
        //batch limit reached, emitting batch
        batchScheduler.advanceTime();
        emitScheduler.advanceTime();
        //batch limit reached, emitting batch
        assertThat(pendingHistogram.count()).isEqualTo(3);
        assertThat(pendingHistogram.totalAmount()).isEqualTo(3); // 1 + 2 + 0

        AssertSubscriber<String> subscriber3 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_C).subscribe(subscriber3);
        assertThat(pendingHistogram.count()).isEqualTo(4);
        assertThat(pendingHistogram.totalAmount()).isEqualTo(4); // 1 + 2 + 0 + 1

        clock.add(Duration.ofNanos(100));
        verify(bulkProvider, times(1)).apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B)));
        assertThat(completionTimer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(0);
        //assertThat(delayTimer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(100);

        subscriber1.assertNoValues().assertNotTerminated();
        subscriber2.assertNoValues().assertNotTerminated();

        clock.add(Duration.ofNanos(100));
        delayScheduler.advanceTimeBy(Duration.ofMillis(50));
        emitScheduler.advanceTime();
        subscriber1.assertResult(RESULT_A);
        subscriber2.assertResult(RESULT_B);
        assertThat(completionTimer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(400); // 200 + 200
        //FIXME: needs better scheduler control
        //assertThat(delayTimer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(100);
    }

    private RequiredSearch dimensionalMetric(String name) {
        return meterRegistry.get(METRICS_QUALIFIER + "_" + name)
            .tag(TAG_KEY, TAG_VALUE);
    }

    @Test
    void should_wait_only_maximum_time_for_calls_before_delegating() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider)).build();
        when(bulkProvider.apply((List<Integer>) argThat(contains(CONTEXT_A))))
            .thenReturn(Mono.just(List.of(RESULT_A)));

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A).subscribe(subscriber1);

        scheduler.advanceTimeBy(Duration.ofMillis(100)); //we move time with maximum wait time
        batchScheduler.advanceTime();
        emitScheduler.advanceTime();
        verify(bulkProvider, times(1)).apply((List<Integer>) argThat(contains(CONTEXT_A)));
        subscriber1.assertResult(RESULT_A);
    }

    @Test
    void should_ignore_empty_batch() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider)).build();
        when(bulkProvider.apply((List<Integer>) argThat(contains(CONTEXT_A))))
            .thenReturn(Mono.just(List.of(RESULT_A)));

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A).subscribe(subscriber1);

        scheduler.advanceTimeBy(Duration.ofMillis(100)); //we move time with maximum wait time
        batchScheduler.advanceTime();
        emitScheduler.advanceTime();
        verify(bulkProvider, times(1)).apply((List<Integer>) argThat(contains(CONTEXT_A)));
        subscriber1.assertResult(RESULT_A);
        scheduler.advanceTimeBy(Duration.ofMillis(100)); //we move time again with maximum wait time
        batchScheduler.advanceTime();
        emitScheduler.advanceTime();
        //verify no more calls were made to provider
        verify(bulkProvider, times(1)).apply((List<Integer>) argThat(contains(CONTEXT_A)));
    }

    @Test
    void should_propagate_bulk_load_error_to_each_value() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider)).build();
        // 2 contexts, bulk error => 2 onError
        IllegalStateException exception = new IllegalStateException("expected error");
        when(bulkProvider.apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B))))
            .thenReturn(Mono.error(exception));

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A).subscribe(subscriber1);
        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_B).subscribe(subscriber2);
        scheduler.advanceTime();
        batchScheduler.advanceTime();
        verify(bulkProvider, times(1)).apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B)));
        emitScheduler.advanceTime();
        subscriber1.assertError(exception);
        subscriber2.assertError(exception);
    }

    @Test
    void should_continue_collapsing_after_load_error() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider)).build();
        IllegalStateException exception = new IllegalStateException("expected error");
        when(bulkProvider.apply((List<Integer>) argThat(containsInAnyOrder(CONTEXT_A, CONTEXT_B))))
            .thenReturn(Mono.error(exception))
            .thenReturn(Mono.just(List.of(RESULT_A, RESULT_B)));

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A).subscribe(subscriber1);
        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_B).subscribe(subscriber2);
        scheduler.advanceTime();
        batchScheduler.advanceTime();
        verify(bulkProvider, times(1)).apply((List<Integer>) argThat(containsInAnyOrder(CONTEXT_A, CONTEXT_B)));
        emitScheduler.advanceTime();
        subscriber1.assertError(exception);
        subscriber2.assertError(exception);

        subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_B).subscribe(subscriber1);
        subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A).subscribe(subscriber2);

        scheduler.advanceTime();
        batchScheduler.advanceTime();
        verify(bulkProvider, times(2)).apply((List<Integer>) argThat(containsInAnyOrder(CONTEXT_B, CONTEXT_A)));
        emitScheduler.advanceTime();
        subscriber1.assertResult(RESULT_B);
        subscriber2.assertResult(RESULT_A);
    }

    @Test
    void should_be_thread_safe() {
        int batchSize = 7;
        int maxConcurrency = 8;
        int maxWaitTimeForBatch = 50;
        int numberOfThreads = 10;
        int numberOfIdsPerThread = 100;
        int highestId = 50;
        int errorRate = 15;
        int rngSeed = 3;
        Random rng = new Random(rngSeed);
        when(bulkProvider.apply(anyList())).thenAnswer(ie -> {
            Mono<List<String>> ret;
            if (rng.nextInt(100) < errorRate) {
                ret = Mono.error(new IllegalArgumentException("test error from bulkProvider"));
            } else {
                ret = Mono.delay(Duration.ofMillis(rng.nextInt(50) + 50))
                    .map(i -> ((List<Integer>) ie.getArgument(0)).stream().map(String::valueOf).collect(toList()))
                    .publishOn(Schedulers.parallel());
            }
            return ret;
        });
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider))
            .withMaximumWaitTime(Duration.ofMillis(maxWaitTimeForBatch))
            .withBatchSize(batchSize)
            .withScheduler(Schedulers.parallel())
            .withBatchScheduler(Schedulers.immediate())
            .withBatchMaxConcurrency(maxConcurrency)
            .withEmitScheduler(Schedulers.immediate())
            .build();
        RequestCollapser<Integer, String> requestCollapser = RequestCollapser.builder(collapsedProvider)
            .withScheduler(Schedulers.immediate())
            .releaseWhenFinished()
            .build();
        List<CompletableFuture<Void>> tasks = IntStream.range(0, numberOfThreads).mapToObj(thread -> CompletableFuture.runAsync(() -> {
            LOG.info("Start thread");
            List<Integer> idsRolled = new CopyOnWriteArrayList<>();
            List<String> idsFinished = new CopyOnWriteArrayList<>();
            //generate
            AssertSubscriber<List<String>> subscriber = AssertSubscriber.create();
            Flux.range(0, numberOfIdsPerThread)
                .map(i -> rng.nextInt(highestId))
                .doOnNext(idsRolled::add)
                .flatMap(id -> requestCollapser.apply(id)
                    .doOnError(e -> LOG.error("id={} error={}", id, e.toString()))
                    .onErrorResume(IllegalArgumentException.class, e -> Mono.empty()))
                .collectList()
                .doOnSuccess(idsFinished::addAll)
                .subscribe(subscriber);
            subscriber.await();
            assertThat(idsFinished).isSubsetOf(idsRolled.stream().map(String::valueOf).collect(toList()));
            subscriber.assertOneResult(v -> assertThat(v).isSubsetOf(idsRolled.stream().map(String::valueOf).collect(toList())));
            LOG.info("Finished {}", idsFinished);
        }, Executors.newFixedThreadPool(1))).collect(toList());
        tasks.forEach(v -> {
            try {
                v.get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException("Unexpected error occurred.", e);
            }
        });
    }

    @Test
    void should_complete_for_not_matched_contexts() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider)).build();
        // 2 contexts, 1 value => 1 onSuccess, 1 onComplete
        when(bulkProvider.apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B))))
            .thenReturn(Mono.delay(Duration.ofMillis(50), delayScheduler).map(i -> List.of(RESULT_B)));

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A).subscribe(subscriber1);
        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_B).subscribe(subscriber2);
        scheduler.advanceTime();

        batchScheduler.advanceTime();
        verify(bulkProvider, times(1)).apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B)));
        subscriber1.assertNoValues().assertNotTerminated();
        subscriber2.assertNoValues().assertNotTerminated();

        delayScheduler.advanceTimeBy(Duration.ofMillis(50));
        emitScheduler.advanceTime();

        subscriber1.assertResult();
        subscriber2.assertResult(RESULT_B);
    }

    @Test
    void should_complete_for_values_where_match_failed() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider)).build();
        // 2 contexts, 2 values, 1 match fails => 1 onSuccess, 1 onComplete + log
        when(bulkProvider.apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B))))
            .thenReturn(Mono.delay(Duration.ofMillis(50), delayScheduler).map(i -> List.of(RESULT_B, "a")));

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A).subscribe(subscriber1);
        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_B).subscribe(subscriber2);
        scheduler.advanceTime();

        batchScheduler.advanceTime();
        verify(bulkProvider, times(1)).apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B)));
        subscriber1.assertNoValues().assertNotTerminated();
        subscriber2.assertNoValues().assertNotTerminated();
        delayScheduler.advanceTimeBy(Duration.ofMillis(50));
        emitScheduler.advanceTime();
        subscriber1.assertResult();
        subscriber2.assertResult(RESULT_B);
    }

    @Test
    void should_be_able_to_shutdown_gracefully() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider)).build();
        lenient().when(bulkProvider.apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B))))
            .thenReturn(Mono.just(List.of(RESULT_A)));

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A).subscribe(subscriber1);
        collapsedProvider.cancel();

        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_B).subscribe(subscriber2);

        batchScheduler.advanceTime();
        verify(bulkProvider, never()).apply(anyList());
        emitScheduler.advanceTime();
        subscriber1.assertNoValues().assertNotTerminated();
        subscriber2.assertNoValues().assertNotTerminated();
    }

    @Test
    void should_limit_bulk_calls_concurrency() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider))
            .withScheduler(Schedulers.parallel())
            .withBatchScheduler(Schedulers.parallel())
            .withEmitScheduler(Schedulers.immediate())
            .withBatchSize(5)
            .withBatchMaxConcurrency(2)
            .withGroupId("test-collapser")
            .build();
        //the throughput will be 10 ids per seconds (batch of 5 * parallelism 2) at 1 sec delayed execution

        when(bulkProvider.apply(anyList()))
            .thenAnswer(invocation -> {
                List<Integer> params = invocation.getArgument(0);
                LOG.debug("Returning delayed batched items for {}", params);
                Thread.sleep(1000);
                return Flux.fromIterable(params)
                    .map(Object::toString)
                    .collectList()
                    .doOnSuccess(b -> LOG.debug("Emitting batched items {}", b));
            });

        Flux.range(1, 15).flatMap(i -> collapsedProvider.apply(i))
            .ignoreElements() // doesn't matter is some elements are emitted successfully
            .as(StepVerifier::create)
            .verifyErrorSatisfies(e -> assertThat(e)
                .isInstanceOf(BulkheadFullException.class)
                .hasMessageContaining("Bulkhead 'test-collapser' is full"));
    }

    @Test
    void should_not_limit_emitted_items_count_by_max_concurrency() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider))
            .withScheduler(Schedulers.parallel())
            .withBatchScheduler(Schedulers.parallel())
            .withEmitScheduler(Schedulers.immediate())
            .withBatchSize(5)
            .withBatchMaxConcurrency(2)
            .withGroupId("test-collapser")
            .build();
        //the throughput will be 10 ids per seconds (batch of 5 * parallelism 2) at 1 sec delayed execution

        when(bulkProvider.apply(anyList()))
            .thenAnswer(invocation -> {
                List<Integer> params = invocation.getArgument(0);
                LOG.debug("Returning delayed batched items for {}", params);
                Thread.sleep(1000);
                return Flux.fromIterable(params)
                    .map(Object::toString)
                    .collectList()
                    .doOnSuccess(b -> LOG.debug("Emitting batched items {}", b));
            });

        Flux.range(1, 5).flatMap(i -> collapsedProvider.apply(i))
            .as(StepVerifier::create)
            .expectNextCount(5)
            .verifyComplete();
    }

    @Test
    void should_not_execute_more_bulk_calls_in_parallel_but_wait_for_it() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider))
            .withScheduler(Schedulers.parallel())
            .withMaximumWaitTime(Duration.ofMillis(200))
            .withBatchSize(5)
            .withBatchScheduler(Schedulers.parallel())
            .withBatchMaxConcurrency(2)
            .withBatchMaxConcurrencyWaitTime(Duration.ofMillis(160))
            .withEmitScheduler(Schedulers.immediate())
            .build();
        //the throughput will be 10 ids per seconds (batch of 5 * parallelism 2) at 1 sec delayed execution

        when(bulkProvider.apply(anyList()))
            .thenAnswer(invocation -> {
                List<Integer> params = invocation.getArgument(0);
                LOG.debug("Returning delayed batched items for {}", params);
                Thread.sleep(100);
                return Flux.fromIterable(params)
                    .map(Object::toString)
                    .collectList()
                    .doOnSuccess(b -> LOG.debug("Emitting batched items {}", b));
            });

        Flux.range(1, 20).flatMap(i -> collapsedProvider.apply(i))
            .as(StepVerifier::create)
            .expectNextCount(20)
            .verifyComplete();
    }

    @Test
    void should_not_execute_more_bulk_calls_in_parallel_with_delay() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider))
            .withScheduler(Schedulers.parallel())
            .withMaximumWaitTime(Duration.ofMillis(200))
            .withBatchSize(5)
            .withBatchScheduler(Schedulers.parallel())
            .withBatchMaxConcurrency(2)
            .withEmitScheduler(Schedulers.immediate())
            .withGroupId("test-collapser")
            .build();
        //the throughput will be 10 ids per seconds (batch of 5 * parallelism 2) at 1 sec delayed execution

        when(bulkProvider.apply(anyList()))
            .thenAnswer(ia -> {
                List<Integer> params = (List<Integer>) ia.getArguments()[0];
                LOG.debug("Returning delayed batched items for {}", params);
                return Flux.fromIterable(params)
                    .map(Object::toString)
                    .collectList()
                    .delayElement(Duration.ofMillis(1000))
                    .doOnSuccess(b -> LOG.debug("Emitting batched items {}", b));
            });

        Flux.range(1, 100).flatMap(i -> collapsedProvider.apply(i))
            .ignoreElements() // doesn't matter is some elements are emitted successfully
            .as(StepVerifier::create)
            .verifyErrorSatisfies(e -> assertThat(e)
                .isInstanceOf(BulkheadFullException.class)
                .hasMessageContaining("Bulkhead 'test-collapser' is full"));
    }

    @Test
    void should_not_execute_more_bulk_calls_in_parallel_with_delay_but_wait_for_it() {
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider))
            .withScheduler(Schedulers.parallel())
            .withBatchSize(5)
            .withBatchScheduler(Schedulers.parallel())
            .withBatchMaxConcurrency(2)
            .withBatchMaxConcurrencyWaitTime(Duration.ofMillis(160))
            .withEmitScheduler(Schedulers.immediate())
            .build();
        //the throughput will be 10 ids per seconds (batch of 5 * parallelism 2) at 1 sec delayed execution

        when(bulkProvider.apply(anyList()))
            .thenAnswer(ia -> {
                List<Integer> params = (List<Integer>) ia.getArguments()[0];
                LOG.debug("Returning delayed batched items for {}", params);
                return Flux.fromIterable(params)
                    .map(Object::toString)
                    .collectList()
                    .delayElement(Duration.ofMillis(100))
                    .doOnSuccess(b -> LOG.debug("Emitting batched items {}", b));
            });

        Flux.range(1, 20).flatMap(i -> collapsedProvider.apply(i))
            .as(StepVerifier::create)
            .expectNextCount(20)
            .verifyComplete();
    }

    /**
     * The collapser must handle if the provider returns {@link Mono#empty()}.
     */
    @Test
    void should_complete_if_contract_is_not_followed() {
        doReturn(Mono.empty()).when(bulkProvider).apply(any());
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsOver(bulkProvider)).build();

        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A).subscribe(subscriber1);
        scheduler.advanceTimeBy(Duration.ofMillis(150));
        batchScheduler.advanceTime();
        emitScheduler.advanceTime();
        subscriber1.assertResult();
    }

    @Test
    void should_maintain_expected_MDC_values() {
        //Given
        collapsedProvider = FanOutRequestCollapser.collapseCallsOver(bulkProvider)
            .withContextValueMatcher((ctx, value) -> ctx.equals(Integer.parseInt(value)))
            .withMaximumWaitTime(Duration.ofMillis(100))
            .withBatchSize(2)
            .build();
        when(bulkProvider.apply((List<Integer>) argThat(contains(CONTEXT_A, CONTEXT_B))))
            .thenReturn(Mono.just((List<String>) List.of(RESULT_B, RESULT_A)).delayElement(Duration.ofMillis(50)));
        //When
        MDC.put(MDC_KEY, "a");
        AssertSubscriber<String> subscriber1 = AssertSubscriber.create();
        collapsedProvider.apply(CONTEXT_A)
            .map(e -> Optional.ofNullable(MDC.get(MDC_KEY)).orElse("n/a"))
            .subscribe(subscriber1);
        AssertSubscriber<String> subscriber2 = AssertSubscriber.create();
        MDC.put(MDC_KEY, "b");
        collapsedProvider.apply(CONTEXT_B)
            .map(e -> Optional.ofNullable(MDC.get(MDC_KEY)).orElse("n/a"))
            .subscribe(subscriber2);
        //Then
        subscriber1.await();
        subscriber2.await();
        subscriber1.assertResult("a");
        subscriber2.assertResult("b");
    }

    @Test
    void should_eagerly_emmit_already_completed_elements_in_eager_mode() throws InterruptedException {
        ArgumentCaptor<List<Integer>> captor = ArgumentCaptor.forClass(List.class);
        doReturn(Flux.just(RESULT_B, RESULT_A).concatWith(Flux.just(RESULT_C).delayElements(Duration.ofMillis(100))))
            .when(eagerBulkProvider).apply(captor.capture());
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsEagerlyOver(eagerBulkProvider))
            .withBatchSize(3)
            .build();

        var countDown = new CountDownLatch(3);
        collapsedProvider.apply(CONTEXT_A)
            .subscribeOn(Schedulers.parallel())
            .log("context-1.")
            .subscribe(result -> {
                assertThat(result).isEqualTo(RESULT_A);
                scheduler.advanceTimeBy(Duration.ofMillis(75));
                countDown.countDown();
            });
        collapsedProvider.apply(CONTEXT_B)
            .subscribeOn(Schedulers.parallel())
            .log("context-2.")
            .subscribe(result -> {
                assertThat(result).isEqualTo(RESULT_B);
                scheduler.advanceTimeBy(Duration.ofMillis(75));
                countDown.countDown();
            });
        collapsedProvider.apply(CONTEXT_C)
            .subscribeOn(Schedulers.parallel())
            .log("context-3.")
            .subscribe(result -> {
                assertThat(result).isEqualTo(RESULT_C);
                countDown.countDown();
            });

        assertThat(countDown.await(1, TimeUnit.SECONDS)).describedAs("Couldn't finish calls successfully in time.").isTrue();
        assertThat(captor.getValue()).containsExactlyInAnyOrder(CONTEXT_A, CONTEXT_B, CONTEXT_C);
        verify(eagerBulkProvider, only()).apply(any());
    }

    @Test
    void should_complete_call_unanswered_by_bulk_response_in_eager_mode() throws InterruptedException {
        ArgumentCaptor<List<Integer>> captor = ArgumentCaptor.forClass(List.class);
        doReturn(Flux.just(RESULT_B, RESULT_A)).when(eagerBulkProvider).apply(captor.capture());
        collapsedProvider = withBaseConfig(FanOutRequestCollapser.collapseCallsEagerlyOver(eagerBulkProvider))
            .withBatchSize(3)
            .build();

        var countDown = new CountDownLatch(3);
        collapsedProvider.apply(CONTEXT_A)
            .subscribeOn(Schedulers.parallel())
            .log("context-1.")
            .subscribe(result -> {
                assertThat(result).isEqualTo(RESULT_A);
                scheduler.advanceTimeBy(Duration.ofMillis(75));
                countDown.countDown();
            });
        collapsedProvider.apply(CONTEXT_B)
            .subscribeOn(Schedulers.parallel())
            .log("context-2.")
            .subscribe(result -> {
                assertThat(result).isEqualTo(RESULT_B);
                scheduler.advanceTimeBy(Duration.ofMillis(75));
                countDown.countDown();
            });
        collapsedProvider.apply(CONTEXT_C)
            .subscribeOn(Schedulers.parallel())
            .log("context-3.")
            .subscribe(result -> {
                throw new IllegalStateException("No value should be sent");
            }, error -> {
                throw new IllegalStateException("Unexpected error", error);
            }, countDown::countDown);

        assertThat(countDown.await(1, TimeUnit.SECONDS)).describedAs("Couldn't finish calls successfully in time.").isTrue();
        assertThat(captor.getValue()).containsExactlyInAnyOrder(CONTEXT_A, CONTEXT_B, CONTEXT_C);
        verify(eagerBulkProvider, only()).apply(any());
    }

    @Test
    void should_delay_error_in_eager_mode() throws InterruptedException {
        ArgumentCaptor<List<Integer>> captor = ArgumentCaptor.forClass(List.class);
        var errorContainingFlux = Flux.just(RESULT_B, RESULT_C + "error", RESULT_A)
            .flatMap(result -> result.endsWith("error") ? Mono.error(IllegalStateException::new) : Mono.just(result));
        doReturn(errorContainingFlux).when(eagerBulkProvider).apply(captor.capture());
        collapsedProvider = FanOutRequestCollapser.collapseCallsEagerlyOver(eagerBulkProvider)
            .withContextValueMatcher((context, value) -> context.equals(Integer.parseInt(value.substring(0, 1))))
            .withBatchSize(3)
            .build();

        var countDown = new CountDownLatch(3);
        collapsedProvider.apply(CONTEXT_A)
            .subscribeOn(Schedulers.parallel())
            .log("context-1.")
            .subscribe(result -> shouldHaveThrown(IllegalStateException.class), error -> {
                assertThat(error).isInstanceOf(IllegalStateException.class);
                countDown.countDown();
            });
        collapsedProvider.apply(CONTEXT_B)
            .subscribeOn(Schedulers.parallel())
            .log("context-2.")
            .subscribe(result -> {
                assertThat(result).isEqualTo(RESULT_B);
                countDown.countDown();
            });
        collapsedProvider.apply(CONTEXT_C)
            .subscribeOn(Schedulers.parallel())
            .log("context-3.")
            .subscribe(result -> shouldHaveThrown(IllegalStateException.class), error -> {
                assertThat(error).isInstanceOf(IllegalStateException.class);
                countDown.countDown();
            });

        assertThat(countDown.await(1, TimeUnit.SECONDS)).describedAs("Couldn't finish calls successfully in time.").isTrue();
        assertThat(captor.getValue()).containsExactlyInAnyOrder(CONTEXT_A, CONTEXT_B, CONTEXT_C);
        verify(eagerBulkProvider, only()).apply(any());
    }

    @Test
    void should_emmit_elements_if_error_comes_last_in_eager_mode() throws InterruptedException {
        ArgumentCaptor<List<Integer>> captor = ArgumentCaptor.forClass(List.class);
        var errorContainingFlux = Flux.just(RESULT_B, RESULT_C + "error", RESULT_A).flatMap(result -> result.endsWith("error")
            ? Mono.error(IllegalStateException::new).delaySubscription(Duration.ofMillis(100L))
            : Mono.just(result).delaySubscription(Duration.ofMillis(10L)));
        doReturn(errorContainingFlux).when(eagerBulkProvider).apply(captor.capture());
        collapsedProvider = FanOutRequestCollapser.collapseCallsEagerlyOver(eagerBulkProvider)
            .withContextValueMatcher((context, value) -> context.equals(Integer.parseInt(value.substring(0, 1))))
            .withBatchSize(3)
            .build();

        var countDown = new CountDownLatch(3);
        collapsedProvider.apply(CONTEXT_C)
            .subscribeOn(Schedulers.parallel())
            .log("context-3.")
            .subscribe(result -> shouldHaveThrown(IllegalStateException.class), error -> {
                assertThat(error).isInstanceOf(IllegalStateException.class);
                countDown.countDown();
            });
        collapsedProvider.apply(CONTEXT_B)
            .subscribeOn(Schedulers.parallel())
            .log("context-2.")
            .subscribe(result -> {
                assertThat(result).isEqualTo(RESULT_B);
                countDown.countDown();
            });
        collapsedProvider.apply(CONTEXT_A)
            .subscribeOn(Schedulers.parallel())
            .log("context-1.")
            .subscribe(result -> {
                assertThat(result).isEqualTo(RESULT_A);
                countDown.countDown();
            });

        assertThat(countDown.await(1, TimeUnit.SECONDS)).describedAs("Couldn't finish calls successfully in time.").isTrue();
        assertThat(captor.getValue()).containsExactlyInAnyOrder(CONTEXT_A, CONTEXT_B, CONTEXT_C);
        verify(eagerBulkProvider, only()).apply(any());
    }
}
