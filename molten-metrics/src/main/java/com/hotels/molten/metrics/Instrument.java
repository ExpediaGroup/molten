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

package com.hotels.molten.metrics;

import static com.hotels.molten.core.metrics.MetricsSupport.name;
import static java.util.Objects.requireNonNull;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.Value;

import com.hotels.molten.core.metrics.MetricId;

/**
 * A utility class to create measured {@link Function}, {@link Consumer}, {@link Supplier} or {@link Callable} and report metrics.
 * An {@link Exception} thrown from the wrapped object is considered as a failure. This behaviour can be overridden if a specific exception is a business exception.
 * This decision can be done in the optionally set {@link Predicate}. Business exceptions are considered as error.
 */
@Value
final class Instrument {
    private static final String SUCCESS_SUFFIX = ".success";
    private static final String FAILED_SUFFIX = ".failed";
    private static final String ERROR_SUFFIX = ".error";

    private final Predicate<Exception> businessExceptionDecisionMaker;
    private final MeterRegistry meterRegistry;
    private final String qualifier;

    Instrument(AbstractBuilder<?, ?> builder) {
        businessExceptionDecisionMaker = requireNonNull(builder.businessExceptionDecisionMaker);
        meterRegistry = requireNonNull(builder.meterRegistry);
        qualifier = requireNonNull(builder.qualifier);
    }

    /**
     * Creates an {@link Instrument} builder.
     *
     * @param meterRegistry this registry will be used to get contexts
     * @return the builder
     */
    public static Builder builder(MeterRegistry meterRegistry) {
        return new Builder(meterRegistry);
    }

    /**
     * Creates an instrumenting {@link Callable} over <code>wrappedCallable</code>.
     *
     * @param wrappedCallable what should be measured
     * @param <T> type of the return value
     * @return an instrumenting {@link Callable} that returns the object returned by the given <code>wrappedCallable</code>.
     */
    public <T> Callable<T> callable(Callable<T> wrappedCallable) {
        return () -> call(wrappedCallable);
    }

    /**
     * Creates an instrumenting {@link Supplier} over <code>wrappedSupplier</code>.
     *
     * @param wrappedSupplier what should be measured
     * @param <T> type of the return value
     * @return an instrumenting {@link Supplier} that returns the object returned by the given <code>wrappedSupplier</code>.
     */
    public <T> Supplier<T> supplier(Supplier<T> wrappedSupplier) {
        return () -> {
            try {
                return call(wrappedSupplier::get);
            } catch (Exception e) {
                throw propagate(e);
            }
        };
    }

    /**
     * Creates an instrumenting {@link Function} over <code>wrappedFunction</code>.
     *
     * @param wrappedFunction what should be measured
     * @param <T> type of the input value
     * @param <R> type of the return value
     * @return an instrumenting {@link Function} that passes the input parameter to <code>wrappedFunction</code> and returns the object what that returns.
     */
    public <T, R> Function<T, R> function(Function<T, R> wrappedFunction) {
        return t -> {
            try {
                return call(() -> wrappedFunction.apply(t));
            } catch (Exception e) {
                throw propagate(e);
            }
        };
    }

    /**
     * Creates an instrumenting {@link Consumer} over <code>wrappedConsumer</code>.
     *
     * @param wrappedConsumer what should be measured
     * @param <T> type of the input value
     * @return an instrumenting {@link Consumer} that passes the input parameter to the given <code>wrappedConsumer</code>.
     */
    public <T> Consumer<T> consumer(Consumer<T> wrappedConsumer) {
        return t -> {
            try {
                call(() -> {
                    wrappedConsumer.accept(t);
                    return null;
                });
            } catch (Exception e) {
                throw propagate(e);
            }
        };
    }

    /**
     * Creates an instrumenting {@link Runnable} over <code>wrappedRunnable</code>.
     *
     * @param wrappedRunnable what should be measured
     * @return an instrumenting {@link Runnable}.
     */
    public Runnable runnable(Runnable wrappedRunnable) {
        return () -> {
            try {
                call(() -> {
                    wrappedRunnable.run();
                    return null;
                });
            } catch (Exception e) {
                throw propagate(e);
            }
        };
    }
    private RuntimeException propagate(Throwable throwable) {
        throwIfUnchecked(throwable);
        throw new RuntimeException(throwable);
    }

    private void throwIfUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
    }

    /**
     * Executes a callable and instruments it.
     *
     * @param callable the callable to call
     * @param <T>      the generics type of the {@link Callable}
     * @return the result of the invoked callable
     * @throws Exception
     */
    <T> T call(Callable<T> callable) throws Exception {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            final T result = callable.call();
            sample.stop(getTimer("success"));
            return result;
        } catch (Exception e) {
            if (businessExceptionDecisionMaker.test(e)) {
                sample.stop(getTimer("error"));
            } else {
                sample.stop(getTimer("failed"));
            }
            throw e;
        }
    }

    private Timer getTimer(String status) {
        return MetricId.builder()
            .name(qualifier)
            .hierarchicalName(name(qualifier, status))
            .tag(Tag.of("status", status))
            .build()
            .toTimer()
            .register(meterRegistry);
    }

    /**
     * Abstract builder for any instrument specific classes.
     *
     * @param <T> the built object type
     * @param <B> the current builder type
     */
    abstract static class AbstractBuilder<T, B extends AbstractBuilder<T, B>> {

        private final MeterRegistry meterRegistry;
        private String qualifier;
        private Predicate<Exception> businessExceptionDecisionMaker = e -> false;

        AbstractBuilder(MeterRegistry meterRegistry) {
            this.meterRegistry = requireNonNull(meterRegistry);
        }

        /**
         * Sets the qualifier parameter.
         *
         * @param qualifier the qualifier
         * @return this builder instance
         */
        public B withQualifier(String qualifier) {
            this.qualifier = requireNonNull(qualifier);
            return (B) this;
        }

        /**
         * Sets the businessExceptionDecisionMaker parameter.
         *
         * @param businessExceptionDecisionMaker the businessExceptionDecisionMaker
         * @return this builder instance
         */
        public B withBusinessExceptionDecisionMaker(Predicate<Exception> businessExceptionDecisionMaker) {
            this.businessExceptionDecisionMaker = requireNonNull(businessExceptionDecisionMaker);
            return (B) this;
        }

        abstract T build();
    }

    /**
     * Builder for {@link Instrument}.
     */
    public static final class Builder extends AbstractBuilder<Instrument, Builder> {

        private Builder(MeterRegistry meterRegistry) {
            super(meterRegistry);
        }

        /**
         * Builds the {@link Instrument} instance based on this builder.
         *
         * @return the {@link Instrument} instance
         */
        @Override
        public Instrument build() {
            return new Instrument(this);
        }
    }
}
