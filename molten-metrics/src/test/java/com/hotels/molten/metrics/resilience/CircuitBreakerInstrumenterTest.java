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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.AbstractDoubleAssert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Unit test for {@link CircuitBreakerInstrumenter}.
 */
public class CircuitBreakerInstrumenterTest {
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

    @Test
    public void should_instrument_with_hierarchical_metrics() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        // Given
        var circuitBreaker = CircuitBreaker.of("id", CircuitBreakerConfig.custom()
            .slowCallDurationThreshold(Duration.ofMillis(200))
            .build());
        CircuitBreakerInstrumenter.builder()
            .meterRegistry(meterRegistry)
            .metricsQualifier(QUALIFIER)
            .hierarchicalMetricsQualifier(HIERARCHICAL_QUALIFIER)
            .componentTagName(COMPONENT_TAG_NAME)
            .componentTagValue(COMPONENT_TAG_VALUE)
            .operationTagName(OPERATION_TAG_NAME)
            .operationTagValue(OPERATION_TAG_VALUE)
            .build().instrument(circuitBreaker);
        // When
        circuitBreaker.acquirePermission();
        circuitBreaker.onSuccess(123, MILLISECONDS);
        circuitBreaker.onSuccess(321, MILLISECONDS);
        circuitBreaker.acquirePermission();
        circuitBreaker.onError(234, MILLISECONDS, new NullPointerException());
        // Then
        assertThatHierarchicalGaugeFor("successful").isEqualTo(2D);
        assertThatHierarchicalGaugeFor("failed").isEqualTo(1D);
        assertThatHierarchicalGaugeFor("buffered").isEqualTo(3D);
        assertThatHierarchicalGaugeFor("failure").isEqualTo(-1D);
        assertThatHierarchicalGaugeFor("rejected").isEqualTo(0D);
        assertThatHierarchicalGaugeFor("status").isEqualTo(0D);
        assertThatHierarchicalGaugeFor("slowness").isEqualTo(-1D);
        assertThatHierarchicalGaugeFor("slow").isEqualTo(2D);
        assertThatHierarchicalGaugeFor("slow-successful").isEqualTo(1D);
        assertThatHierarchicalGaugeFor("slow-failed").isEqualTo(1D);
    }

    private AbstractDoubleAssert<?> assertThatHierarchicalGaugeFor(String indicator) {
        return assertThat(meterRegistry.get(HIERARCHICAL_QUALIFIER + ".circuit." + indicator).gauge().value());
    }

    @Test
    public void should_instrument_with_dimensional_metrics() {
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        // Given
        var circuitBreaker = CircuitBreaker.of("id", CircuitBreakerConfig.custom()
            .slowCallDurationThreshold(Duration.ofMillis(200))
            .build());
        CircuitBreakerInstrumenter.builder()
            .meterRegistry(meterRegistry)
            .metricsQualifier(QUALIFIER)
            .hierarchicalMetricsQualifier(HIERARCHICAL_QUALIFIER)
            .componentTagName(COMPONENT_TAG_NAME)
            .componentTagValue(COMPONENT_TAG_VALUE)
            .operationTagName(OPERATION_TAG_NAME)
            .operationTagValue(OPERATION_TAG_VALUE)
            .build().instrument(circuitBreaker);
        // When
        circuitBreaker.acquirePermission();
        circuitBreaker.onSuccess(123, MILLISECONDS);
        circuitBreaker.onSuccess(321, MILLISECONDS);
        circuitBreaker.acquirePermission();
        circuitBreaker.onError(234, MILLISECONDS, new NullPointerException());
        // Then
        assertThatDimensionalGaugeFor("successful").isEqualTo(2D);
        assertThatDimensionalGaugeFor("failed").isEqualTo(1D);
        assertThatDimensionalGaugeFor("buffered").isEqualTo(3D);
        assertThatDimensionalGaugeFor("failure").isEqualTo(-1D);
        assertThatDimensionalGaugeFor("rejected").isEqualTo(0D);
        assertThatDimensionalGaugeFor("status").isEqualTo(0D);
        assertThatDimensionalGaugeFor("slowness").isEqualTo(-1D);
        assertThatDimensionalGaugeFor("slow").isEqualTo(2D);
        assertThatDimensionalGaugeFor("slow-successful").isEqualTo(1D);
        assertThatDimensionalGaugeFor("slow-failed").isEqualTo(1D);
    }

    private AbstractDoubleAssert<?> assertThatDimensionalGaugeFor(String indicator) {
        return assertThat(meterRegistry.get(QUALIFIER + "_" + indicator)
            .tag(COMPONENT_TAG_NAME, COMPONENT_TAG_VALUE)
            .tag(OPERATION_TAG_NAME, OPERATION_TAG_VALUE)
            .gauge().value());
    }
}
