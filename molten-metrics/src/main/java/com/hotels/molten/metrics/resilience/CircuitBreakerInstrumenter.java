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

package com.hotels.molten.metrics.resilience;

import static com.hotels.molten.core.metrics.MetricsSupport.name;

import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.Metrics;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.Builder;
import lombok.NonNull;

import com.hotels.molten.core.metrics.MetricId;

/**
 * Exposes {@link CircuitBreaker} metrics via {@link MeterRegistry}.
 */
@Builder
public class CircuitBreakerInstrumenter {
    private static final String CIRCUIT = "circuit";
    @NonNull
    private final MeterRegistry meterRegistry;
    @NonNull
    private final String metricsQualifier;
    @NonNull
    private final String hierarchicalMetricsQualifier;
    @NonNull
    @Builder.Default
    private final String componentTagName = "component";
    @Nullable
    private final String componentTagValue;
    @NonNull
    @Builder.Default
    private final String operationTagName = "operation";
    @Nullable
    private final String operationTagValue;

    /**
     * Instruments a circuitbreaker with the following metrics.
     * <ul>
     * <li>failure - failure rate</li>
     * <li>failed - number of failed calls</li>
     * <li>buffered - number of buffered calls</li>
     * <li>rejected - number of rejected calls</li>
     * <li>successful - number of successful calls</li>
     * <li>slowness - slow call rate</li>
     * <li>slow - number of slow calls</li>
     * <li>slow-successful - number of successful but slow calls</li>
     * <li>slow-failed - number of failed slow calls</li>
     * <li>status - whether the circuit is closed or not</li>
     * </ul>
     *
     * @param circuitBreaker the circuitbreaker to instrument
     */
    public void instrument(CircuitBreaker circuitBreaker) {
        MetricId metricId = createMetricId();
        gaugeFor(metricId, "failure", circuitBreaker.getMetrics(), Metrics::getFailureRate);
        gaugeFor(metricId, "failed", circuitBreaker.getMetrics(), Metrics::getNumberOfFailedCalls);
        gaugeFor(metricId, "buffered", circuitBreaker.getMetrics(), Metrics::getNumberOfBufferedCalls);
        gaugeFor(metricId, "rejected", circuitBreaker.getMetrics(), Metrics::getNumberOfNotPermittedCalls);
        gaugeFor(metricId, "successful", circuitBreaker.getMetrics(), Metrics::getNumberOfSuccessfulCalls);
        gaugeFor(metricId, "status", circuitBreaker.getState(), State::getOrder);
        gaugeFor(metricId, "slowness", circuitBreaker.getMetrics(), Metrics::getSlowCallRate);
        gaugeFor(metricId, "slow", circuitBreaker.getMetrics(), Metrics::getNumberOfSlowCalls);
        gaugeFor(metricId, "slow-successful", circuitBreaker.getMetrics(), Metrics::getNumberOfSlowSuccessfulCalls);
        gaugeFor(metricId, "slow-failed", circuitBreaker.getMetrics(), Metrics::getNumberOfSlowFailedCalls);
    }

    private MetricId createMetricId() {
        MetricId.MetricIdBuilder metricIdBuilder = MetricId.builder()
            .name(metricsQualifier)
            .hierarchicalName(hierarchicalMetricsQualifier);
        if (componentTagValue != null) {
            metricIdBuilder.tag(Tag.of(componentTagName, componentTagValue));
        }
        if (operationTagValue != null) {
            metricIdBuilder.tag(Tag.of(operationTagName, operationTagValue));
        }
        return metricIdBuilder.build();
    }

    private <T> void gaugeFor(MetricId metricId, String indicator, T target, ToDoubleFunction<T> valueFunction) {
        metricId.extendWith(indicator, name(CIRCUIT, indicator)).toGauge(target, valueFunction).register(meterRegistry);
    }
}
