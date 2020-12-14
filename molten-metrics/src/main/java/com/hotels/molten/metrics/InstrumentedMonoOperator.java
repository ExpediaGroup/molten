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
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;

import com.hotels.molten.core.metrics.MetricId;

/**
 * Instrumented operator for reactor's {@link Mono} operator.
 * @param <T> the type
 */
public class InstrumentedMonoOperator<T> extends MonoOperator<T, T> {
    private final MeterRegistry meterRegistry;
    private final Predicate<Exception> businessExceptionDecisionMaker;
    private final MetricId metricId;

    InstrumentedMonoOperator(Mono source, MeterRegistry meterRegistry, Predicate<Exception> businessExceptionDecisionMaker, MetricId metricId) {
        super(source);
        this.meterRegistry = requireNonNull(meterRegistry);
        this.businessExceptionDecisionMaker = requireNonNull(businessExceptionDecisionMaker);
        this.metricId = requireNonNull(metricId);
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> actual) {
        source.subscribe(new MeasuredSubscriber<>(actual, meterRegistry, businessExceptionDecisionMaker, metricId));
    }
}
