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

import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nullable;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.Builder;
import lombok.NonNull;

import com.hotels.molten.core.metrics.MetricId;

/**
 * Exposes {@link Bulkhead} metrics via {@link MeterRegistry}.
 */
@Builder
public class BulkheadInstrumenter {
    private static final String BULKHEAD = "bulkhead";
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
     * Instruments a bulkhead with the following metrics.
     * <ul>
     * <li>Maximum number of concurrent calls permitted</li>
     * <li>Available concurrent calls</li>
     * <li>Rejected calls so far</li>
     * </ul>
     *
     * @param bulkhead the bulkhead to instrument
     */
    public void instrument(Bulkhead bulkhead) {
        LongAdder rejected = new LongAdder();
        bulkhead.getEventPublisher().onCallRejected(e -> rejected.increment());
        MetricId metricId = createMetricId();
        metricId.extendWith("max", name(BULKHEAD, "max"))
            .toGauge(bulkhead.getBulkheadConfig(), BulkheadConfig::getMaxConcurrentCalls).register(meterRegistry);
        metricId.extendWith("available", name(BULKHEAD, "available"))
            .toGauge(bulkhead.getMetrics(), Bulkhead.Metrics::getAvailableConcurrentCalls).register(meterRegistry);
        metricId.extendWith("rejected", name(BULKHEAD, "rejected"))
            .toGauge(rejected, LongAdder::longValue).register(meterRegistry);
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
}
