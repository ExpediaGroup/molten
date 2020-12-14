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

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import com.hotels.molten.core.metrics.MetricId;

/**
 * A delegating reactive cache with async reloading of values.
 * <br />
 * <img src="doc-files/reactive_reloading_cache.png">
 * <br />
 * For configuration options see {@link Builder} methods.
 *
 * @param <CONTEXT>      the type of context
 * @param <VALUE>        the type of value
 * @param <CACHE_KEY>    the type of cache key
 * @param <CACHED_VALUE> the type of cached value
 */
@Slf4j
public final class ReactiveReloadingCache<CONTEXT, VALUE, CACHE_KEY, CACHED_VALUE> implements ReactiveCache<CONTEXT, VALUE> {
    private final ReactiveCache<CACHE_KEY, TimestampedValue<CACHED_VALUE>> delegate;
    private final Duration timeToRefresh;
    private final Clock clock;
    private final Function<CONTEXT, Mono<VALUE>> loadingFunction;
    private final Function<CONTEXT, Mono<CACHE_KEY>> contextToKey;
    private final Function<VALUE, Mono<CACHED_VALUE>> valueToCachedValue;
    private final Function<CACHED_VALUE, Mono<VALUE>> cachedValueToValue;
    private final Scheduler scheduler;
    private final boolean failSafe;
    private final boolean fallback;
    private final boolean emptyIfNotCached;
    private final LongAdder loadCounter = new LongAdder();
    private final LongAdder loadExceptionCounter = new LongAdder();
    private final LongAdder asyncLoadCounter = new LongAdder();
    private final LongAdder asyncLoadExceptionCounter = new LongAdder();

    private ReactiveReloadingCache(Builder<CONTEXT, VALUE, CACHE_KEY, CACHED_VALUE> builder) {
        delegate = requireNonNull(builder.delegate);
        timeToRefresh = requireNonNull(builder.timeToRefresh);
        clock = requireNonNull(builder.clock);
        loadingFunction = requireNonNull(builder.loadingFunction);
        contextToKey = requireNonNull(builder.contextToKey);
        valueToCachedValue = requireNonNull(builder.valueToCachedValue);
        cachedValueToValue = requireNonNull(builder.cachedValueToValue);
        scheduler = requireNonNull(builder.scheduler);
        failSafe = builder.failSafe;
        fallback = builder.fallback;
        emptyIfNotCached = builder.emptyIfNotCached;
        if (builder.meterRegistry != null) {
            var metricId = MetricId.builder().name("cache").hierarchicalName(builder.metricQualifier).tag(Tag.of("name", requireNonNull(builder.metricQualifier))).build();
            metricId.extendWith("total_load_count", "total-load-count").toGauge(loadCounter, Number::doubleValue).register(builder.meterRegistry);
            metricId.extendWith("total_load_exception_count", "total-load-exception-count").toGauge(loadExceptionCounter, Number::doubleValue).register(builder.meterRegistry);
            metricId.extendWith("async_load_count", "async-load-count").toGauge(asyncLoadCounter, Number::doubleValue).register(builder.meterRegistry);
            metricId.extendWith("async_load_exception_count", "async-load-exception-count").toGauge(asyncLoadExceptionCounter, Number::doubleValue).register(builder.meterRegistry);
        }
    }

    /**
     * Creates a {@link ReactiveReloadingCache} builder over a {@link ReactiveCache}.
     *
     * @param delegate the cache to delegate caching to
     * @param <K> the type of key
     * @param <V> the type of value
     * @return the new builder
     */
    public static <K, V> Builder<K, V, K, V> over(ReactiveCache<K, TimestampedValue<V>> delegate) {
        return new Builder<>(delegate);
    }

    @Override
    public Mono<VALUE> get(CONTEXT context) {
        return toKey(context)
            .flatMap(delegate::get)
            .doOnNext(cachedValue -> {
                if (isReloadNeeded(cachedValue)) {
                    reloadValue(context);
                }
            })
            .onErrorResume(e -> fallback ? Mono.empty() : Mono.error(e))
            .defaultIfEmpty(TimestampedValue.notFound())
            .flatMap(cachedValue -> {
                Mono<VALUE> value;
                if (cachedValue.isNotFound()) {
                    LOG.debug("Cached value not found for context={}", context);
                    if (emptyIfNotCached) {
                        reloadValue(context);
                        value = Mono.empty();
                    } else {
                        value = loadValue(context);
                    }
                } else {
                    value = Mono.just(cachedValue)
                        .filter(TimestampedValue::nonEmpty)
                        .map(TimestampedValue::getValue)
                        .flatMap(cachedValueToValue);
                }
                return value;
            });
    }

    @Override
    public Mono<Void> put(CONTEXT context, VALUE value) {
        return toKey(context)
            .flatMap(cacheKey -> Mono.justOrEmpty(value)
                .flatMap(v -> valueToCachedValue.apply(v)
                    .map(cachedValue -> new TimestampedValue<>(cachedValue, Instant.now(clock))))
                .defaultIfEmpty(emptyTimestampedValue())
                .flatMap(timestampedValue -> delegate.put(cacheKey, timestampedValue)));
    }

    private Mono<CACHE_KEY> toKey(CONTEXT context) {
        return contextToKey.apply(context);
    }

    private TimestampedValue<CACHED_VALUE> emptyTimestampedValue() {
        return new TimestampedValue<>(null, Instant.now(clock));
    }

    private boolean isReloadNeeded(TimestampedValue<CACHED_VALUE> cachedValue) {
        return clock.instant().isAfter(cachedValue.timestamp.plus(timeToRefresh));
    }

    private Mono<VALUE> loadValue(CONTEXT context) {
        loadCounter.increment();
        return loadingFunction.apply(context)
            .doOnSuccess(value -> {
                LOG.debug("Storing item in cache for context={}", context);
                put(context, value)
                    .subscribeOn(scheduler)
                    .subscribe(v -> { }, ex -> LOG.warn("Error caching value for context={} cause={}", context, ex.getMessage()));
            })
            .doOnError(e -> loadExceptionCounter.increment())
            .onErrorResume(e -> failSafe ? Mono.empty() : Mono.error(e));
    }

    private void reloadValue(CONTEXT context) {
        asyncLoadCounter.increment();
        Mono.defer(() -> loadValue(context)) // we must ensure this is async even if the loader is not
            .subscribeOn(scheduler)
            .subscribe(value -> { }, e -> {
                asyncLoadExceptionCounter.increment();
                LOG.warn("Couldn't reload expired value for context={} cause={}", context, e.getMessage());
            });
    }

    /**
     * A value with a timestamp.
     *
     * @param <T> the value type
     */
    @Value
    public static final class TimestampedValue<T> implements Serializable {
        @SuppressWarnings("rawtypes")
        private static final TimestampedValue VOID = new TimestampedValue<>();
        private final T value;
        private final Instant timestamp;

        public TimestampedValue(T value, Instant timestamp) {
            this.value = value;
            this.timestamp = requireNonNull(timestamp);
        }

        private TimestampedValue() {
            this.value = null;
            this.timestamp = null;
        }

        /**
         * Returns true if the wrapped value is non-null.
         *
         * @return true if value exists, otherwise false
         */
        public boolean nonEmpty() {
            return value != null;
        }

        /**
         * Returns true if the wrapped value is not a cached one.
         *
         * @return true if there was no timestamp associated with this value meaning it was not yet cached
         */
        public boolean isNotFound() {
            return timestamp == null;
        }

        /**
         * Gets a default empty value for not found values.
         *
         * @param <V> the actual value type to cast to
         * @return the empty not found value
         */
        @SuppressWarnings("unchecked")
        public static <V> TimestampedValue<V> notFound() {
            return VOID;
        }
    }

    /**
     * Builder for {@link ReactiveReloadingCache}.
     *
     * @param <CONTEXT>      the type of context
     * @param <VALUE>        the type of value
     * @param <CACHE_KEY>    the type of cache key
     * @param <CACHED_VALUE> the type of cached value
     */
    public static final class Builder<CONTEXT, VALUE, CACHE_KEY, CACHED_VALUE> {
        private final ReactiveCache<CACHE_KEY, TimestampedValue<CACHED_VALUE>> delegate;
        private Duration timeToRefresh;
        private Clock clock = Clock.systemUTC();
        private Function<CONTEXT, Mono<VALUE>> loadingFunction;
        private Function<CONTEXT, Mono<CACHE_KEY>> contextToKey;
        private Function<VALUE, Mono<CACHED_VALUE>> valueToCachedValue;
        private Function<CACHED_VALUE, Mono<VALUE>> cachedValueToValue;
        private Scheduler scheduler = Schedulers.boundedElastic();
        private boolean failSafe;
        private boolean fallback;
        private boolean emptyIfNotCached;
        private MeterRegistry meterRegistry;
        private String metricQualifier;

        @SuppressWarnings("unchecked")
        private Builder(ReactiveCache<CACHE_KEY, TimestampedValue<CACHED_VALUE>> delegate) {
            this.delegate = requireNonNull(delegate);
            this.contextToKey = k -> Mono.just((CACHE_KEY) k);
            this.valueToCachedValue = v -> Mono.just((CACHED_VALUE) v);
            this.cachedValueToValue = v -> Mono.just((VALUE) v);
        }

        /**
         * Sets the loader function which will populate the cache for missing entries.
         * <p>
         * The resulting Mono will be subscribed to using the current scheduler for sync loading, and using {@link #scheduler} for async reloading.
         *
         * @param loadingFunction the loading function which can produce the value for a given context
         * @return this builder
         */
        @SuppressWarnings("unchecked")
        public Builder<CONTEXT, VALUE, CACHE_KEY, CACHED_VALUE> loadingWith(Function<CONTEXT, Mono<VALUE>> loadingFunction) {
            this.loadingFunction = requireNonNull(loadingFunction);
            this.contextToKey = k -> Mono.just((CACHE_KEY) k);
            this.valueToCachedValue = v -> Mono.just((CACHED_VALUE) v);
            this.cachedValueToValue = v -> Mono.just((VALUE) v);
            return this;
        }

        /**
         * Sets the loader function which will populate the cache for missing entries.
         * Also sets converters to transform {@link CONTEXT} to {@link CACHE_KEY} and {@link VALUE} to/from {@link CACHED_VALUE}.
         * <p>
         * The resulting Mono will be subscribed to using the current scheduler for sync loading, and using {@link #scheduler} for async reloading.
         *
         * @param loadingFunction    the loading function which can produce the value for a given context
         * @param contextToKey       the context to cache key converter
         * @param valueToCachedValue the value to cached value converter
         * @param cachedValueToValue the cached value to value converter
         * @param <C> the context type
         * @param <V> the value type
         * @return this builder
         */
        @SuppressWarnings("unchecked")
        public <C, V> Builder<C, V, CACHE_KEY, CACHED_VALUE> loadingWith(Function<C, Mono<V>> loadingFunction, Function<C, Mono<CACHE_KEY>> contextToKey,
                                                                         Function<V, Mono<CACHED_VALUE>> valueToCachedValue, Function<CACHED_VALUE, Mono<V>> cachedValueToValue) {
            Builder<C, V, CACHE_KEY, CACHED_VALUE> builder = (Builder<C, V, CACHE_KEY, CACHED_VALUE>) this;
            builder.loadingFunction = requireNonNull(loadingFunction);
            builder.contextToKey = requireNonNull(contextToKey);
            builder.valueToCachedValue = requireNonNull(valueToCachedValue);
            builder.cachedValueToValue = requireNonNull(cachedValueToValue);
            return builder;
        }

        /**
         * Sets the time to refresh after write. If this duration is elapsed after the value was cached the next subsequent get will trigger an async reload.
         *
         * @param ttr the TTR duration
         * @return this builder
         */
        public Builder<CONTEXT, VALUE, CACHE_KEY, CACHED_VALUE> withTimeToRefresh(Duration ttr) {
            timeToRefresh = requireNonNull(ttr);
            checkState(timeToRefresh.getSeconds() > 0, "Time to refresh duration must be positive");
            return this;
        }

        /**
         * Sets the scheduler on which the async reloading should happen.
         *
         * @param scheduler the scheduler
         * @return this builder
         */
        public Builder<CONTEXT, VALUE, CACHE_KEY, CACHED_VALUE> withScheduler(Scheduler scheduler) {
            this.scheduler = requireNonNull(scheduler);
            return this;
        }

        /**
         * Whether to enable fail-safe loading.
         * If enabled then any error from loading the value will be suppressed and treated as empty result.
         *
         * @return this builder
         */
        public Builder<CONTEXT, VALUE, CACHE_KEY, CACHED_VALUE> failSafeLoading() {
            this.failSafe = true;
            return this;
        }

        /**
         * Whether to enable loading fallback for failed cache gets.
         * If enabled then any error from getting the cached value from the underlying cache will fall back to invoke the loading function to get the value.
         *
         * @return this builder
         */
        public Builder<CONTEXT, VALUE, CACHE_KEY, CACHED_VALUE> fallbackLoading() {
            this.fallback = true;
            return this;
        }

        /**
         * Whether to enable returning empty result when key was not found in cache.
         * If enabled then if a key was not found in the underlying cache then an empty result is returned and an async reloading is triggered.
         *
         * @return this builder
         */
        public Builder<CONTEXT, VALUE, CACHE_KEY, CACHED_VALUE> emptyIfNotCached() {
            this.emptyIfNotCached = true;
            return this;
        }

        /**
         * Sets the meter registry to report cache reload metrics with.
         *
         * @param meterRegistry the meter registry to report to
         * @param qualifier     the metrics qualifier to register metrics at
         * @return this builder
         */
        public Builder<CONTEXT, VALUE, CACHE_KEY, CACHED_VALUE> withMetrics(MeterRegistry meterRegistry, String qualifier) {
            this.meterRegistry = requireNonNull(meterRegistry);
            this.metricQualifier = requireNonNull(qualifier);
            return this;
        }

        /**
         * Sets the clock being used for timestamps. Used for testing.
         *
         * @param clock the clock to use
         * @return this builder
         */
        Builder<CONTEXT, VALUE, CACHE_KEY, CACHED_VALUE> withClock(Clock clock) {
            this.clock = requireNonNull(clock);
            return this;
        }

        /**
         * Builds the cache according to this builder's settings.
         *
         * @return the cache
         */
        public ReactiveReloadingCache<CONTEXT, VALUE, CACHE_KEY, CACHED_VALUE> build() {
            return new ReactiveReloadingCache<>(this);
        }
    }
}
