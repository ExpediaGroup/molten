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
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import com.hotels.molten.core.MoltenCore;
import com.hotels.molten.core.metrics.MetricId;

/**
 * A request collapser implementation over functions returning reactive types.
 * <br />
 * <img src="doc-files/request_collapser_ttl.png">
 * <br />
 * The wrapped function has the following characteristics:
 * <ul>
 * <li>The first call to a specific context will be delegated and cached.</li>
 * <li>Each subsequent invocations to same context will get the cached one (with single subscription, see {@link Mono#cache()}.</li>
 * <li>Once the first call emits any event (success, completed, error) that is propagated to each subsequent subscriber with same context.</li>
 * <li>By default the requests are stored for 10 seconds and last 1000 unique contexts.</li>
 * </ul>
 * Please note that calls are cached by {@code context} so it should have proper {@code hashCode} and {@code equals} implementation.
 * If you need more fine-grained configuration please use the {@link #builder(Function)}.
 * <p>
 * When {@link MeterRegistry} is set with {@link Builder#withMetrics(MeterRegistry, MetricId)} then registers the following metrics:
 * <ul>
 * <li>{@code [qualifier].pending} - histogram for number of on-going collapsed request</li>
 * <li>{@code [qualifier].pending.current} - gauge for number on-going collapsed request</li>
 * </ul>
 * <p>
 * <h3>Retrying collapsed calls</h3>
 * Be sure not to {@link Mono#retry()} collapsed invocation as it will always be the same (up to TTL).
 * If you set {@link Builder#releaseWhenFinished()} then you can retry if you wrap collapsed invocation in a {@link Mono#defer(java.util.function.Supplier)}.
 * <h3>Timeout</h3>
 * One can set {@link Builder#timeOutIn(Duration)} to add a timeout to each collapsed call to avoid being stuck if downstream never completes.
 *
 * @param <CONTEXT> the context type
 * @param <VALUE>   the value type
 */
@Slf4j
public final class RequestCollapser<CONTEXT, VALUE> implements Function<CONTEXT, Mono<VALUE>> {
    private final Map<CONTEXT, Mono<VALUE>> onGoingCallsStore;
    private final Function<CONTEXT, Mono<VALUE>> valueProvider;
    private final Scheduler scheduler;
    private final boolean releaseWhenFinished;
    private final AtomicInteger pendingItemCounter = new AtomicInteger();
    private final Duration timeOut;
    private final Scheduler timeOutScheduler;
    private DistributionSummary pendingItemsHistogram;

    private RequestCollapser(Builder<CONTEXT, VALUE> builder) {
        this.onGoingCallsStore = getOnGoingCallsStore(builder);
        valueProvider = requireNonNull(builder.valueProvider);
        scheduler = requireNonNull(builder.scheduler);
        releaseWhenFinished = builder.releaseWhenFinished;
        timeOut = builder.timeOut;
        timeOutScheduler = builder.timeOutScheduler;
        if (builder.meterRegistry != null) {
            pendingItemsHistogram = builder.metricId.extendWith("pending_count", "pending")
                .toDistributionSummary()
                .description("Number of pending items")
                .register(builder.meterRegistry);
            builder.metricId.extendWith("pending_current_count", "pending.current")
                .toGauge(pendingItemCounter, AtomicInteger::get)
                .register(builder.meterRegistry);
        }
    }

    @Override
    public Mono<VALUE> apply(CONTEXT context) {
        LOG.debug("Collapsing context={}", context);
        if (pendingItemsHistogram != null) {
            pendingItemsHistogram.record(pendingItemCounter.incrementAndGet());
        }
        Mono<VALUE> valueMono = onGoingCallsStore.computeIfAbsent(context, this::promiseOf);
        if (pendingItemsHistogram != null) {
            valueMono = valueMono.doFinally(i -> pendingItemsHistogram.record(pendingItemCounter.decrementAndGet()));
        }
        return valueMono
            .transform(MoltenCore.propagateContext());
    }

    /**
     * Creates a {@link RequestCollapser} builder over a value provider.
     *
     * @param valueProvider the value provider to collapse calls over
     * @param <C> the context type
     * @param <V> the value type
     * @return the builder
     */
    public static <C, V> Builder<C, V> builder(Function<C, Mono<V>> valueProvider) {
        return new Builder<>(valueProvider);
    }

    /**
     * Wraps existing provider with one that collapses similar calls.
     * Uses default configuration of 10 seconds collapse window and 1000 unique calls tracked.
     * If you need more fine-grained configuration please use {@link #builder(Function)}.
     *
     * @param valueProvider the {@link Mono} provider
     * @param <C>           the context type
     * @param <V>           the value type
     * @return the enhanced provider method
     */
    public static <C, V> RequestCollapser<C, V> collapseCallsOn(Function<C, Mono<V>> valueProvider) {
        return RequestCollapser.builder(valueProvider).build();
    }

    /**
     * Gets the current number of on-going calls.
     *
     * @return the number of calls
     */
    int getNumberOfOnGoingCalls() {
        return onGoingCallsStore.size();
    }

    private Map<CONTEXT, Mono<VALUE>> getOnGoingCallsStore(Builder<CONTEXT, VALUE> builder) {
        Map<CONTEXT, Mono<VALUE>> onGoingCallsStore = builder.onGoingCallsStore;
        if (onGoingCallsStore == null) {
            Cache<CONTEXT, Mono<VALUE>> cache = Caffeine.newBuilder()
                .maximumSize(builder.maxCollapsedCalls)
                .expireAfterWrite(builder.maxCollapsedTime.toMillis(), TimeUnit.MILLISECONDS)
                .build();
            onGoingCallsStore = cache.asMap();
        }
        return onGoingCallsStore;
    }

    private Mono<VALUE> promiseOf(CONTEXT context) {
        LOG.debug("New collapser for context={}", context);
        Mono<VALUE> promise;
        if (releaseWhenFinished) {
            promise = valueProvider.apply(context)
                .doFinally(i -> {
                    LOG.debug("Releasing context={}", context);
                    onGoingCallsStore.remove(context);
                })
                .cache()
                .publishOn(scheduler);
        } else {
            promise = valueProvider.apply(context)
                .cache()
                .publishOn(scheduler);
        }
        if (timeOut != null) {
            promise = promise.timeout(timeOut, timeOutScheduler);
        }
        return promise;
    }

    /**
     * Builder for {@link RequestCollapser}.
     *
     * @param <CONTEXT> the context type
     * @param <VALUE>   the value type
     */
    public static final class Builder<CONTEXT, VALUE> {
        private final Function<CONTEXT, Mono<VALUE>> valueProvider;
        private Map<CONTEXT, Mono<VALUE>> onGoingCallsStore;
        private Scheduler scheduler = Schedulers.parallel();
        private boolean releaseWhenFinished;
        private int maxCollapsedCalls = 1000;
        private Duration maxCollapsedTime = Duration.of(10, SECONDS);
        private MeterRegistry meterRegistry;
        private MetricId metricId;
        private Duration timeOut;
        private Scheduler timeOutScheduler = Schedulers.parallel();

        private Builder(Function<CONTEXT, Mono<VALUE>> valueProvider) {
            this.valueProvider = requireNonNull(valueProvider);
        }

        /**
         * Sets the scheduler to emit data with. Defaults to {@link Schedulers#parallel()}.
         *
         * @param scheduler the scheduler
         * @return this builder instance
         */
        public Builder<CONTEXT, VALUE> withScheduler(Scheduler scheduler) {
            this.scheduler = requireNonNull(scheduler);
            return this;
        }

        /**
         * Sets the maximum number of unique collapsed calls to track at once.
         * Defaults to 1000 unique elements.
         *
         * @param maxCollapsedCalls the maximum number of collapsed calls
         * @return this builder instance
         */
        public Builder<CONTEXT, VALUE> withMaxCollapsedCalls(int maxCollapsedCalls) {
            checkArgument(maxCollapsedCalls > 0, "maxCollapsedCalls must be positive");
            this.maxCollapsedCalls = maxCollapsedCalls;
            return this;
        }

        /**
         * Sets the maximum timeframe to collapse similar calls after the initial request.
         * Defaults to 10 seconds.
         *
         * @param maxCollapsedTime the maximum timeframe
         * @return this builder instance
         */
        public Builder<CONTEXT, VALUE> withMaxCollapsedTime(Duration maxCollapsedTime) {
            this.maxCollapsedTime = requireNonNull(maxCollapsedTime);
            return this;
        }

        /**
         * Sets the store for ongoing calls. Usually it is safer to use a cache with TTL to avoid potential leak.
         * Ignores {@link #withMaxCollapsedCalls(int)} and {@link #withMaxCollapsedTime(Duration)} when set.
         *
         * @param onGoingCallsStore the store for ongoing calls.
         * @return this builder instance
         */
        public Builder<CONTEXT, VALUE> withOnGoingCallsStore(Map<CONTEXT, Mono<VALUE>> onGoingCallsStore) {
            this.onGoingCallsStore = requireNonNull(onGoingCallsStore);
            return this;
        }

        /**
         * Sets whether to release collapsed calls as soon as they are complete.
         * <br />
         * <img src="doc-files/request_collapser_release.png">
         *
         * @return this builder instance
         */
        public Builder<CONTEXT, VALUE> releaseWhenFinished() {
            releaseWhenFinished = true;
            return this;
        }

        /**
         * Sets whether to add an extra safety by adding timeout to delegated calls. This is a safety net if the collapsed function is not behaving nicely and never complete.
         *
         * @param timeOut the timeout to maximum wait for execution
         * @return this builder instance
         */
        public Builder<CONTEXT, VALUE> timeOutIn(Duration timeOut) {
            this.timeOut = requireNonNull(timeOut);
            return this;
        }

        /**
         * Sets the scheduler to be used for timeouts. Defaults to parallel.
         *
         * @param timeOutScheduler the scheduler
         * @return this builder instance
         */
        Builder<CONTEXT, VALUE> withTimeOutScheduler(Scheduler timeOutScheduler) {
            this.timeOutScheduler = requireNonNull(timeOutScheduler);
            return this;
        }

        /**
         * Sets the meter registry and metric ID base to record statistics with.
         *
         * @param meterRegistry the registry to register metrics with
         * @param metricId the metric id under which metrics should be registered
         * @return this builder instance
         */
        public Builder<CONTEXT, VALUE> withMetrics(MeterRegistry meterRegistry, MetricId metricId) {
            this.meterRegistry = requireNonNull(meterRegistry);
            this.metricId = requireNonNull(metricId);
            return this;
        }

        /**
         * Builds the {@link RequestCollapser} instance based on this builder.
         *
         * @return the {@link RequestCollapser} instance
         */
        public RequestCollapser<CONTEXT, VALUE> build() {
            return new RequestCollapser<>(this);
        }
    }
}
