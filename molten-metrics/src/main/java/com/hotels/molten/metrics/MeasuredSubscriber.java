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

import java.util.function.Predicate;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.hotels.molten.core.metrics.MetricId;

/**
 * Subscriber which instruments any reactor operator.
 */
@RequiredArgsConstructor
final class MeasuredSubscriber<T> implements Subscriber<T> {
    @NonNull
    private final Subscriber<? super T> actual;
    @NonNull
    private final MeterRegistry meterRegistry;
    @NonNull
    private final Predicate<Exception> businessExceptionDecisionMaker;
    @NonNull
    private final MetricId metricId;

    private Timer.Sample sample;

    @Override
    public void onSubscribe(Subscription s) {
        sample = Timer.start(meterRegistry);
        actual.onSubscribe(s);
    }

    @Override
    public void onNext(T t) {
        actual.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        String status;
        if (t instanceof Exception && businessExceptionDecisionMaker.test((Exception) t)) {
            status = "error";
        } else {
            status = "failed";
        }
        sample.stop(getTimer(status));
        actual.onError(t);
    }

    @Override
    public void onComplete() {
        sample.stop(getTimer("success"));
        actual.onComplete();
    }

    private Timer getTimer(String status) {
        return metricId.toBuilder()
            .hierarchicalName(name(metricId.getHierarchicalName(), status))
            .tag(Tag.of("status", status))
            .build()
            .toTimer()
            .register(meterRegistry);
    }
}
