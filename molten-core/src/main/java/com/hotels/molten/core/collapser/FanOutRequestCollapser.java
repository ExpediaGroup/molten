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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import com.hotels.molten.core.MoltenCore;
import com.hotels.molten.core.metrics.MetricId;

/**
 * A request collapser acting as a middle man waiting for multiple calls and delegating them in chunks to a provider with bulk API and also responding to each caller with their
 * respective result.
 * <br>
 * <img src="doc-files/fan_out_request_collapser.png" alt="Fan out request collapser sequence diagram">
 * <p>
 * What this does:
 * <ul>
 * <li>Gathers calls by context and delegates them to bulk provider in chunks. See {@link Builder#withBatchSize(int)}.</li>
 * <li>Waits for a full chunk maximum for a given time. See {@link Builder#withMaximumWaitTime(Duration)}.</li>
 * <li>Propagates returned values to respective caller by matching value with its context. See {@link Builder#withContextValueMatcher(BiFunction)}.</li>
 * <li>Propagates result on a given {@link Scheduler}. See {@link Builder#withScheduler(Scheduler)}.</li>
 * <li>Bulk provider error is propagated to each caller taking part in that call {@link #collapseCallsOver(Function)}  in lazy mode},
 * or to each not yet answered caller in {@link #collapseCallsEagerlyOver(Function)}  in eager mode}.</li>
 * <li>Non-matched contexts will get empty result.</li>
 * </ul>
 * <p>
 * What this does NOT do:
 * <ul>
 * <li>Doesn't deduplicate contexts. If you need such add a {@link RequestCollapser} on top of this.</li>
 * <li>Doesn't retry failed bulk calls. You can add a {@link Mono#retry()} on top of this to requeue failed ones and/or add retry under this to retry whole bulk call.</li>
 * <li>Doesn't respect back-pressure.</li>
 * </ul>
 * <p>
 * When {@link MeterRegistry} is set with {@link Builder#withMetrics(MeterRegistry, MetricId)} then registers the following metrics:
 * <ul>
 * <li>{@code [qualifier].item.pending} - number of pending items where the batch operation hasn't been started yet</li>
 * <li>{@code [qualifier].item.delay} - the delay items are waiting in queue before delegated to bulk provider</li>
 * <li>{@code [qualifier].item.completion} - the delay between items getting in the queue and their respective result are returned</li>
 * </ul>
 *
 * @param <CONTEXT> the context type
 * @param <VALUE>   the VALUE type
 */
@Slf4j
public final class FanOutRequestCollapser<CONTEXT, VALUE> implements Function<CONTEXT, Mono<VALUE>> {
    private final Sinks.Many<ContextWithSubject<CONTEXT, VALUE>> callSink = Sinks.unsafe().many().unicast().onBackpressureBuffer();
    private final BiFunction<CONTEXT, VALUE, Boolean> contextValueMatcher;
    private final Disposable callsWaitingSubscription;
    private Timer delayTimer;
    private Timer completionTimer;
    private MeterRegistry meterRegistry;
    private DistributionSummary pendingItemsHistogram;
    private final AtomicInteger pendingItemCounter = new AtomicInteger();
    private DistributionSummary batchSizeHistogram;
    private final AtomicInteger partialBatchCounter = new AtomicInteger();
    private final int batchSize;

    private FanOutRequestCollapser(Builder<CONTEXT, VALUE> builder) {
        this.contextValueMatcher = requireNonNull(builder.contextValueMatcher);
        registerMetrics(builder);
        batchSize = builder.batchSize;
        var groupId = builder.groupId;
        var bulkhead = Bulkhead.of(groupId, BulkheadConfig.custom()
            .maxConcurrentCalls(builder.maxConcurrency)
            .maxWaitDuration(Optional.ofNullable(builder.maxConcurrencyWaitTime).orElse(Duration.ZERO))
            .build());
        callsWaitingSubscription = callSink.asFlux()
            .publishOn(builder.scheduler)
            .bufferTimeout(batchSize, builder.maximumWaitTime, builder.scheduler)
            .filter(batch -> !batch.isEmpty())
            .flatMap(contextsWithSubjects -> {
                List<CONTEXT> contexts = toContexts(contextsWithSubjects);
                LOG.debug("Executing batch with contexts={}, groupId={}", contexts, groupId);
                updateMetrics(contextsWithSubjects);
                final List<ContextWithSubject<CONTEXT, VALUE>> contextsWithSubjectsToDrain = new LinkedList<>(contextsWithSubjects);
                return Mono.just(contexts)
                    .flatMapMany(builder.bulkProvider)
                    .subscribeOn(builder.batchScheduler)
                    .flatMap(value -> bindValueToContext(contextsWithSubjectsToDrain, value, groupId))
                    .concatWith(bindEmptyToRemaining(contextsWithSubjectsToDrain))
                    .transform(BulkheadOperator.of(bulkhead))
                    .doOnError(e -> LOG.error("Error while delegating call for contexts={}, groupId={}", contexts, groupId, e))
                    .onErrorResume(throwable -> bindErrorToRemaining(contextsWithSubjectsToDrain, throwable));
            }, Integer.MAX_VALUE)
            .publishOn(builder.emitScheduler)
            .doOnNext(this::logEmission)
            .subscribe(element -> element.propagateToSubject(completionTimer),
                e -> LOG.error("Error during call scheduling and the request collapser shut down. groupId={}", groupId, e),
                () -> LOG.info("The request collapser has been shut down. groupId={}", groupId));
    }

    @Override
    public Mono<VALUE> apply(CONTEXT context) {
        return Mono.justOrEmpty(context)
            .flatMap(s -> {
                if (pendingItemsHistogram != null) {
                    pendingItemsHistogram.record(pendingItemCounter.incrementAndGet());
                }
                ContextWithSubject<CONTEXT, VALUE> contextWithSubject = new ContextWithSubject<>(context, meterRegistry);
                synchronized (callSink) {
                    // since callSink is unsafe, we need to synchronize the requests by hand
                    // emitNext is basically an offer to a queue, so not really blocking too much here
                    callSink.emitNext(contextWithSubject, Sinks.EmitFailureHandler.FAIL_FAST);
                }
                return contextWithSubject.subject;
            })
            .transform(MoltenCore.propagateContext());
    }

    /**
     * Creates a {@link FanOutRequestCollapser} builder over a bulk provider, emitting all the bulk results in one piece.<br>
     * If you would rather emmit bulk results in several pieces, {@link #collapseCallsEagerlyOver} should be used instead.
     *
     * @param <C>          the context type
     * @param <V>          the VALUE type
     * @param bulkProvider the bulk provider to delegate calls to
     * @return the builder
     */
    public static <C, V> Builder<C, V> collapseCallsOver(Function<List<C>, Mono<List<V>>> bulkProvider) {
        return new Builder<>(requireNonNull(bulkProvider).andThen(listMono -> listMono.flatMapMany(Flux::fromIterable)));
    }

    /**
     * Creates a {@link FanOutRequestCollapser} builder over a bulk provider, possibly emitting the bulk results in several pieces.
     * The eagerly emitted results are eagerly sent to the respective callers by the fan-out request collapser.<br>
     * If you would rather emmit bulk results in one piece, {@link #collapseCallsOver} should be used instead.
     *
     * @param <C>          the context type
     * @param <V>          the VALUE type
     * @param bulkProvider the bulk provider to delegate calls to
     * @return the builder
     */
    public static <C, V> Builder<C, V> collapseCallsEagerlyOver(Function<List<C>, Flux<V>> bulkProvider) {
        return new Builder<>(bulkProvider);
    }

    /**
     * Cancels the waiting calls and ignores subsequent ones.
     */
    public void cancel() {
        if (!callsWaitingSubscription.isDisposed()) {
            callsWaitingSubscription.dispose();
        }
    }

    private void registerMetrics(Builder<CONTEXT, VALUE> builder) {
        if (builder.meterRegistry != null) {
            meterRegistry = builder.meterRegistry;
            var metricId = builder.metricId;
            delayTimer = metricId.extendWith("item_delay", "item.delay").toTimer().register(meterRegistry);
            completionTimer = metricId.extendWith("item_completion", "item.completion").toTimer().register(meterRegistry);
            pendingItemsHistogram = metricId.extendWith("pending", "item.pending").toDistributionSummary()
                .description("Number of pending items")
                .register(meterRegistry);
            metricId.extendWith("pending_current_count", "item.pending.current").toGauge(pendingItemCounter, AtomicInteger::get)
                .register(meterRegistry);
            batchSizeHistogram = metricId.extendWith("batch_size", "batch.size").toDistributionSummary()
                .description("Size of executed batches")
                .register(meterRegistry);
            metricId.extendWith("partial_batch_count", "batch.partial.count").toGauge(partialBatchCounter, AtomicInteger::get)
                .register(meterRegistry);
        }
    }

    private void updateMetrics(List<ContextWithSubject<CONTEXT, VALUE>> contextsWithSubjects) {
        if (delayTimer != null) {
            pendingItemsHistogram.record(pendingItemCounter.addAndGet(-1 * contextsWithSubjects.size()));
            contextsWithSubjects.forEach(c -> c.sample.stop(delayTimer));
            batchSizeHistogram.record(contextsWithSubjects.size());
            if (contextsWithSubjects.size() < batchSize) {
                partialBatchCounter.incrementAndGet();
            }
        }
    }

    private void logEmission(ContextWithValue<CONTEXT, VALUE> item) {
        if (LOG.isDebugEnabled()) {
            if (item.error != null) {
                LOG.debug("Emitting error for context={} error={}", item.contextWithSubject.context, item.error.toString());
            } else if (item.value != null) {
                LOG.debug("Emitting item for context={} item={}", item.contextWithSubject.context, item.value);
            } else {
                LOG.debug("Emitting empty item for context={}", item.contextWithSubject.context);
            }
        }
    }

    private List<CONTEXT> toContexts(List<ContextWithSubject<CONTEXT, VALUE>> contextWithSubjects) {
        return contextWithSubjects.stream().map(ContextWithSubject::getContext).collect(toUnmodifiableList());
    }

    private Mono<ContextWithValue<CONTEXT, VALUE>> bindValueToContext(List<ContextWithSubject<CONTEXT, VALUE>> contextsWithSubjects, VALUE value, String groupId) {
        synchronized (contextsWithSubjects) {
            return Mono.justOrEmpty(getFirstMatch(contextsWithSubjects, value, groupId))
                .map(contextWithSubject -> ContextWithValue.value(contextWithSubject, value));
        }
    }

    private Optional<ContextWithSubject<CONTEXT, VALUE>> getFirstMatch(List<ContextWithSubject<CONTEXT, VALUE>> contextsWithSubjects, VALUE value, String groupId) {
        return contextsWithSubjects.stream()
            .filter(contextWithSubject -> matchContextByValue(contextWithSubject.context, value, groupId))
            .findFirst()
            .map(firstMatch -> {
                contextsWithSubjects.remove(firstMatch);
                return firstMatch;
            });
    }

    private Flux<ContextWithValue<CONTEXT, VALUE>> bindEmptyToRemaining(List<ContextWithSubject<CONTEXT, VALUE>> contextsWithSubjects) {
        synchronized (contextsWithSubjects) {
            return Flux.fromIterable(contextsWithSubjects).map(ContextWithValue::empty);
        }
    }

    private Flux<ContextWithValue<CONTEXT, VALUE>> bindErrorToRemaining(List<ContextWithSubject<CONTEXT, VALUE>> contextsWithSubjects, Throwable throwable) {
        synchronized (contextsWithSubjects) {
            return Flux.fromIterable(contextsWithSubjects).map(contextWithSubject -> ContextWithValue.error(contextWithSubject, throwable));
        }
    }

    private boolean matchContextByValue(CONTEXT context, VALUE value, String groupId) {
        boolean matches;
        try {
            matches = contextValueMatcher.apply(context, value);
        } catch (Exception e) {
            LOG.warn("Failed to match value={} with context={} by groupId={}, error={}", value, context, groupId, e.toString());
            matches = false;
        }
        return matches;
    }

    @Value
    @EqualsAndHashCode(of = "context")
    @ToString(of = "context")
    private static final class ContextWithSubject<C, V> {
        private final C context;
        private final Timer.Sample sample;
        private final Sinks.One<V> sink = Sinks.one();
        private final Mono<V> subject = sink.asMono();

        private ContextWithSubject(C context, MeterRegistry meterRegistry) {
            this.context = requireNonNull(context);
            this.sample = meterRegistry != null ? Timer.start(meterRegistry) : null;
        }
    }

    @Value
    @EqualsAndHashCode(of = "value")
    @ToString(of = "value")
    private static final class ContextWithValue<C, V> {
        @NonNull
        private final ContextWithSubject<C, V> contextWithSubject;
        private final V value;
        private final Throwable error;

        private static <CE, VE> ContextWithValue<CE, VE> empty(ContextWithSubject<CE, VE> contextWithSubject) {
            return new ContextWithValue<>(contextWithSubject, null, null);
        }

        private static <CE, VE> ContextWithValue<CE, VE> value(ContextWithSubject<CE, VE> contextWithSubject, VE value) {
            return new ContextWithValue<>(contextWithSubject, value, null);
        }

        private static <CE, VE> ContextWithValue<CE, VE> error(ContextWithSubject<CE, VE> contextWithSubject, Throwable error) {
            return new ContextWithValue<>(contextWithSubject, null, error);
        }

        private void propagateToSubject(Timer completionTimer) {
            recordCompletion(completionTimer);
            if (error != null) {
                contextWithSubject.sink.emitError(error, Sinks.EmitFailureHandler.FAIL_FAST);
            } else if (value != null) {
                contextWithSubject.sink.emitValue(value, Sinks.EmitFailureHandler.FAIL_FAST);
            } else {
                contextWithSubject.sink.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);
            }
        }

        private void recordCompletion(Timer completionTimer) {
            if (contextWithSubject.sample != null) {
                contextWithSubject.sample.stop(completionTimer);
            }
        }
    }

    /**
     * Builder for {@link FanOutRequestCollapser}.
     *
     * @param <C> the context type
     * @param <V> the VALUE type
     */
    public static final class Builder<C, V> {
        private final Function<List<C>, Flux<V>> bulkProvider;
        private int maxConcurrency = Runtime.getRuntime().availableProcessors();
        private BiFunction<C, V, Boolean> contextValueMatcher;
        private Scheduler scheduler = Schedulers.parallel();
        private Scheduler emitScheduler = Schedulers.immediate();
        private int batchSize = 10;
        private Scheduler batchScheduler = Schedulers.parallel();
        private Duration maximumWaitTime = Duration.ofMillis(200);
        private Duration maxConcurrencyWaitTime;
        private MeterRegistry meterRegistry;
        private MetricId metricId;
        private String groupId = "unknown";

        private Builder(Function<List<C>, Flux<V>> bulkProvider) {
            this.bulkProvider = requireNonNull(bulkProvider);
        }

        /**
         * Sets the context-value matcher to be used to match a value with a given context.
         *
         * @param contextValueMatcher the context-value matcher
         * @return this builder instance
         */
        public Builder<C, V> withContextValueMatcher(BiFunction<C, V, Boolean> contextValueMatcher) {
            this.contextValueMatcher = requireNonNull(contextValueMatcher);
            return this;
        }

        /**
         * Sets the request collapser's group id to be identified by in logs and bulkheads.<br>
         * If not set, the events logged by the request collapser cannot be distinguished by the events logged by others.<br>
         * Note that the uniqueness of the id is not forced out but strongly recommended.
         *
         * @param groupId the unique id of the request collapser
         * @return this builder instance
         */
        public Builder<C, V> withGroupId(String groupId) {
            this.groupId = requireNonNull(groupId);
            return this;
        }

        /**
         * Sets the scheduler to be used to trigger batches.
         * By default it is using {@link Schedulers#parallel()}.
         *
         * @param scheduler the scheduler
         * @return this builder instance
         */
        public Builder<C, V> withScheduler(Scheduler scheduler) {
            this.scheduler = requireNonNull(scheduler);
            return this;
        }

        /**
         * Sets the scheduler to be used to emit data.
         * By default it is using {@link Schedulers#immediate()}.
         *
         * @param scheduler the scheduler
         * @return this builder instance
         */
        public Builder<C, V> withEmitScheduler(Scheduler scheduler) {
            this.emitScheduler = requireNonNull(scheduler);
            return this;
        }

        /**
         * Sets the maximum time to wait for a batch to fill up. Defaults to 200 ms.
         *
         * @param maximumWaitTime the maximum time to wait
         * @return this builder instance
         */
        public Builder<C, V> withMaximumWaitTime(Duration maximumWaitTime) {
            checkArgument(maximumWaitTime != null && maximumWaitTime.toMillis() > 0L, "Maximum wait time must be positive");
            this.maximumWaitTime = maximumWaitTime;
            return this;
        }

        /**
         * Sets the batch size. Defaults to 10.
         *
         * @param batchSize the batch size
         * @return this builder instance
         */
        public Builder<C, V> withBatchSize(int batchSize) {
            checkArgument(batchSize > 0, "Batch size must be positive");
            this.batchSize = batchSize;
            return this;
        }

        /**
         * Sets the meter registry and metric ID base to record statistics with.
         *
         * @param meterRegistry the registry to register metrics with
         * @param metricId      the metric id under which metrics should be registered
         * @return this builder instance
         */
        public Builder<C, V> withMetrics(MeterRegistry meterRegistry, MetricId metricId) {
            this.meterRegistry = requireNonNull(meterRegistry);
            this.metricId = requireNonNull(metricId);
            return this;
        }

        /**
         * Sets the scheduler to use to subscribe to batch operation.
         * By default it is using {@link Schedulers#parallel()}.
         *
         * @param batchScheduler the scheduler to execute batch operations on
         * @return this builder instance
         */
        public Builder<C, V> withBatchScheduler(Scheduler batchScheduler) {
            this.batchScheduler = requireNonNull(batchScheduler);
            return this;
        }


        /**
         * Sets the maximum concurrency to fire batch calls.<br>
         * If reached, new batch calls will fail with {@link io.github.resilience4j.bulkhead.BulkheadFullException}.
         * By default it is same as the number of available processors.
         *
         * @param maxConcurrency the maximum concurrency for batch calls
         * @return this builder instance
         * @see #withBatchMaxConcurrencyWaitTime(Duration)
         */
        public Builder<C, V> withBatchMaxConcurrency(int maxConcurrency) {
            checkArgument(maxConcurrency > 0, "Max concurrency should be positive");
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        /**
         * Sets the maximum time to wait for executing a prepared batch call if there are already {@link #withBatchMaxConcurrency(int) max concurrency} batches running.<br>
         * If the wait time expires and the prepared batch call couldn't be started, {@link io.github.resilience4j.bulkhead.BulkheadFullException} is thrown.
         * By default it's {@link Duration#ZERO}, failing fast if the {@link #withBatchMaxConcurrency(int) max concurrency} limit is reached.
         *
         * @param maximumWaitTime the maximum time to wait
         * @return this builder instance
         */
        public Builder<C, V> withBatchMaxConcurrencyWaitTime(Duration maximumWaitTime) {
            checkArgument(maximumWaitTime != null && maximumWaitTime.toMillis() >= 0L, "Maximum wait time must be positive or zero");
            this.maxConcurrencyWaitTime = maximumWaitTime;
            return this;
        }

        /**
         * Builds the {@link FanOutRequestCollapser} instance based on this builder.
         *
         * @return the {@link FanOutRequestCollapser} instance
         */
        public FanOutRequestCollapser<C, V> build() {
            return new FanOutRequestCollapser<>(this);
        }
    }
}
