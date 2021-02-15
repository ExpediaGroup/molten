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

import static com.hotels.molten.core.metrics.MetricsSupport.GRAPHITE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import com.hotels.molten.core.metrics.MoltenMetrics;
import com.hotels.molten.test.AssertSubscriber;

/**
 * Unit test for {@link BulkheadInstrumenter}.
 */
public class BulkheadInstrumenterTest {
    private static final String QUALIFIER = "qualifier";
    private static final String HIERARCHICAL_QUALIFIER = "hierarchical.qualifier";
    private static final String COMPONENT_TAG_NAME = "component";
    private static final String COMPONENT_TAG_VALUE = "comp-value";
    private static final String OPERATION_TAG_NAME = "operation";
    private static final String OPERATION_TAG_VALUE = "op-value";
    private MeterRegistry meterRegistry;

    @BeforeMethod
    public void initContext() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @AfterMethod
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void should_instrument_bulkhead_with_hierarchical_metrics() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        var instrumenter = BulkheadInstrumenter.builder()
            .meterRegistry(meterRegistry)
            .metricsQualifier(QUALIFIER)
            .hierarchicalMetricsQualifier(HIERARCHICAL_QUALIFIER)
            .componentTagName(COMPONENT_TAG_NAME)
            .componentTagValue(COMPONENT_TAG_VALUE)
            .operationTagName(OPERATION_TAG_NAME)
            .operationTagValue(OPERATION_TAG_VALUE)
            .build();

        Bulkhead bulkhead = Bulkhead.of("bulkhead-name", BulkheadConfig.custom().maxConcurrentCalls(1).build());
        instrumenter.instrument(bulkhead);

        Gauge maxGauge = meterRegistry.get(HIERARCHICAL_QUALIFIER + ".bulkhead.max").gauge();
        Gauge availableGauge = meterRegistry.get(HIERARCHICAL_QUALIFIER + ".bulkhead.available").gauge();
        Gauge rejectedGauge = meterRegistry.get(HIERARCHICAL_QUALIFIER + ".bulkhead.rejected").gauge();
        assertThat(maxGauge.value()).isEqualTo(1);

        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        //this next subscription won't finish until we say so keeps the bulkhead full
        AssertSubscriber<String> subscriber = new AssertSubscriber<>();
        Mono.just("result")
            .delayElement(Duration.ofMillis(5), scheduler)
            .transform(BulkheadOperator.of(bulkhead))
            .subscribe(subscriber);
        assertThat(availableGauge.value()).isEqualTo(0);
        //this one fail at subscription time since bulkhead is full
        StepVerifier.create(Mono.just("result2")
            .transform(BulkheadOperator.of(bulkhead)))
            .verifyError(BulkheadFullException.class);
        //let's advance time to free up bulkhead
        scheduler.advanceTimeBy(Duration.ofMillis(5));
        subscriber.assertResult("result");
        //this one can run since bulkhead is free already
        StepVerifier.create(Mono.just("result3")
            .transform(BulkheadOperator.of(bulkhead)))
            .expectNext("result3")
            .verifyComplete();
        assertThat(availableGauge.value()).isEqualTo(1);
        assertThat(rejectedGauge.value()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void should_instrument_bulkhead_with_dimensional_metrics() {
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        var instrumenter = BulkheadInstrumenter.builder()
            .meterRegistry(meterRegistry)
            .metricsQualifier(QUALIFIER)
            .hierarchicalMetricsQualifier(HIERARCHICAL_QUALIFIER)
            .componentTagName(COMPONENT_TAG_NAME)
            .componentTagValue(COMPONENT_TAG_VALUE)
            .operationTagName(OPERATION_TAG_NAME)
            .operationTagValue(OPERATION_TAG_VALUE)
            .build();

        Bulkhead bulkhead = Bulkhead.of("bulkhead-name", BulkheadConfig.custom().maxConcurrentCalls(1).build());
        instrumenter.instrument(bulkhead);

        Gauge maxGauge = dimensionalGaugeFor("max");
        Gauge availableGauge = dimensionalGaugeFor("available");
        Gauge rejectedGauge = dimensionalGaugeFor("rejected");
        assertThat(maxGauge.value()).isEqualTo(1);

        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        //this next subscription won't finish until we say so keeps the bulkhead full
        AssertSubscriber<String> subscriber = new AssertSubscriber<>();
        Mono.just("result")
            .delayElement(Duration.ofMillis(5), scheduler)
            .transform(BulkheadOperator.of(bulkhead))
            .subscribe(subscriber);
        assertThat(availableGauge.value()).isEqualTo(0);
        //this one fail at subscription time since bulkhead is full
        StepVerifier.create(Mono.just("result2")
            .transform(BulkheadOperator.of(bulkhead)))
            .verifyError(BulkheadFullException.class);
        //let's advance time to free up bulkhead
        scheduler.advanceTimeBy(Duration.ofMillis(5));
        subscriber.assertResult("result");
        //this one can run since bulkhead is free already
        StepVerifier.create(Mono.just("result3")
            .transform(BulkheadOperator.of(bulkhead)))
            .expectNext("result3")
            .verifyComplete();
        assertThat(availableGauge.value()).isEqualTo(1);
        assertThat(rejectedGauge.value()).isEqualTo(1);
    }

    private Gauge dimensionalGaugeFor(String indicator) {
        return meterRegistry.get(QUALIFIER + "_" + indicator)
            .tag(COMPONENT_TAG_NAME, COMPONENT_TAG_VALUE)
            .tag(OPERATION_TAG_NAME, OPERATION_TAG_VALUE)
            .tag(GRAPHITE_ID, HIERARCHICAL_QUALIFIER + ".bulkhead." + indicator)
            .gauge();
    }
}
