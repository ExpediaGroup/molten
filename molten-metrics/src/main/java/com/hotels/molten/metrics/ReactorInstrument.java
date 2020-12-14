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

import static java.util.Objects.requireNonNull;

import java.util.function.Predicate;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.hotels.molten.core.metrics.MetricId;

/**
 * Instrumentation helper class for Reactor.
 */
public final class ReactorInstrument {
    private final MeterRegistry meterRegistry;
    private final MetricId metricId;
    private final Predicate<Exception> businessExceptionDecisionMaker;

    private ReactorInstrument(ReactorInstrumentBuilder builder) {
        meterRegistry = requireNonNull(builder.meterRegistry);
        metricId = requireNonNull(builder.metricId);
        businessExceptionDecisionMaker = requireNonNull(builder.businessExceptionDecisionMaker);
    }

    public static ReactorInstrumentBuilder builder() {
        return new ReactorInstrumentBuilder();
    }

    /**
     * Creates an instrumented {@link Flux}.
     * @param source the source which will be instrumented
     * @param <T> the type
     * @return instrumented {@link Flux}
     */
    public <T> InstrumentedFluxOperator<T> flux(Flux<T> source) {
        return new InstrumentedFluxOperator<>(source, meterRegistry, businessExceptionDecisionMaker, metricId);
    }

    /**
     * Creates an instrumented {@link Mono}.
     * @param source the source which will be instrumented
     * @param <T> the type
     * @return instrumented {@link Mono}
     */
    public <T> InstrumentedMonoOperator<T> mono(Mono<T> source) {
        return new InstrumentedMonoOperator<>(source, meterRegistry, businessExceptionDecisionMaker, metricId);
    }

    public static final class ReactorInstrumentBuilder {
        private MeterRegistry meterRegistry;
        private MetricId metricId;
        private Predicate<Exception> businessExceptionDecisionMaker = e -> false;

        private ReactorInstrumentBuilder() {
        }

        public ReactorInstrumentBuilder meterRegistry(MeterRegistry val) {
            meterRegistry = requireNonNull(val);
            return this;
        }

        public ReactorInstrumentBuilder metricId(MetricId val) {
            metricId = requireNonNull(val);
            return this;
        }

        public ReactorInstrumentBuilder businessExceptionDecisionMaker(Predicate<Exception> val) {
            businessExceptionDecisionMaker = requireNonNull(val);
            return this;
        }

        public ReactorInstrument build() {
            return new ReactorInstrument(this);
        }
    }
}
