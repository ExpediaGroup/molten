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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.FluxSink.OverflowStrategy;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.publisher.UnicastProcessor;
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
 * <li>Bulk provider error is propagated to each caller taking part in that call.</li>
 * <li>Matching error is logged but ignored. Respective call will get empty result.</li>
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
    private final UnicastProcessor<ContextWithSubject<CONTEXT, VALUE>> callsWaiting = UnicastProcessor.create();
    private final FluxSink<ContextWithSubject<CONTEXT, VALUE>> callSink = callsWaiting.sink();
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
        callsWaitingSubscription = callsWaiting
            .publishOn(builder.scheduler)
            .bufferTimeout(batchSize, builder.maximumWaitTime, builder.scheduler)
            .filter(batch -> !batch.isEmpty())
            .flatMap(contextsWithSubjects -> {
                List<CONTEXT> contexts = toContexts(contextsWithSubjects);
                LOG.debug("Executing batch with contexts={}", contexts);
                updateMetrics(contextsWithSubjects);
                return Mono.defer(() -> builder.bulkProvider.apply(contexts))
                    .subscribeOn(builder.batchScheduler)
                    .defaultIfEmpty(List.of())
                    .doOnError(e -> LOG.error("Error while delegating call for contexts={}", contexts, e))
                    .flatMapMany(values -> Flux.fromIterable(contextsWithSubjects).flatMap(context -> bindValueToContext(context, values)))
                    .onErrorResume(throwable -> Flux.fromIterable(contextsWithSubjects).map(context -> ContextWithValue.error(context, throwable)));
            }, builder.maxConcurrency)
            .publishOn(builder.emitScheduler)
            .doOnNext(this::logEmission)
            .subscribe(element -> element.propagateToSubject(completionTimer),
                e -> LOG.error("Error during call scheduling", e),
                () -> LOG.info("Calls waiting stream has completed"));
    }

    @Override
    public Mono<VALUE> apply(CONTEXT context) {
        return Mono.justOrEmpty(context)
            .flatMap(s -> {
                if (pendingItemsHistogram != null) {
                    pendingItemsHistogram.record(pendingItemCounter.incrementAndGet());
                }
                ContextWithSubject<CONTEXT, VALUE> contextWithSubject = new ContextWithSubject<>(context, meterRegistry);
                callSink.next(contextWithSubject);
                return contextWithSubject.subject.next();
            })
            .transform(MoltenCore.propagateContext());
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

    /**
     * Creates a {@link FanOutRequestCollapser} builder over a bulk provider.
     *
     * @param <C> the context type
     * @param <V> the VALUE type
     * @param bulkProvider the bulk provider to delegate calls to
     * @return the builder
     */
    public static <C, V> Builder<C, V> collapseCallsOver(Function<List<C>, Mono<List<V>>> bulkProvider) {
        return new Builder<>(bulkProvider);
    }

    private List<CONTEXT> toContexts(List<ContextWithSubject<CONTEXT, VALUE>> contextWithSubjects) {
        return contextWithSubjects.stream().map(cs -> cs.context).collect(Collectors.toList());
    }

    private Mono<ContextWithValue<CONTEXT, VALUE>> bindValueToContext(ContextWithSubject<CONTEXT, VALUE> contextWithSubject, List<VALUE> values) {
        return Flux.fromIterable(values)
            .filter(value -> matchContextByValue(contextWithSubject.context, value))
            .next()
            .map(value -> ContextWithValue.value(contextWithSubject, value))
            .onErrorResume(e -> Mono.just(ContextWithValue.error(contextWithSubject, e)))
            .defaultIfEmpty(ContextWithValue.empty(contextWithSubject));
    }

    private boolean matchContextByValue(CONTEXT ctx, VALUE value) {
        boolean matches;
        try {
            matches = contextValueMatcher.apply(ctx, value);
        } catch (Exception e) {
            LOG.warn("Failed to match value={} with context={} error={}", value, ctx, e.toString());
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
        private final ReplayProcessor<V> subject = ReplayProcessor.create(1);
        private final FluxSink<V> sink = subject.sink(OverflowStrategy.ERROR);

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
                contextWithSubject.sink.error(error);
            } else if (value != null) {
                contextWithSubject.sink.next(value);
            } else {
                contextWithSubject.sink.complete();
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
        private final Function<List<C>, Mono<List<V>>> bulkProvider;
        private int maxConcurrency = Runtime.getRuntime().availableProcessors();
        private BiFunction<C, V, Boolean> contextValueMatcher;
        private Scheduler scheduler = Schedulers.parallel();
        private Scheduler emitScheduler = Schedulers.immediate();
        private int batchSize = 10;
        private Scheduler batchScheduler = Schedulers.parallel();
        private Duration maximumWaitTime = Duration.ofMillis(200);
        private MeterRegistry meterRegistry;
        private MetricId metricId;

        public Builder(Function<List<C>, Mono<List<V>>> bulkProvider) {
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
         * @param metricId the metric id under which metrics should be registered
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
         * Sets the maximum concurrency to fire batch calls.
         * By default it is same as the number of available processors.
         *
         * @param maxConcurrency the maximum concurrency for batch calls
         * @return this builder instance
         */
        public Builder<C, V> withBatchMaxConcurrency(int maxConcurrency) {
            checkArgument(maxConcurrency > 0, "Max concurrency should be positive");
            this.maxConcurrency = maxConcurrency;
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
