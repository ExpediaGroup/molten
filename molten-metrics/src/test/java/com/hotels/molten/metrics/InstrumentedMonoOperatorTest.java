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

import static com.hotels.molten.core.metrics.MetricsSupport.GRAPHITE_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import com.hotels.molten.core.metrics.MetricId;
import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Unit test for {@link InstrumentedMonoOperator}.
 */
@Slf4j
public class InstrumentedMonoOperatorTest {
    private static final String QUALIFIER = "test";
    private static final String SUCCESSFUL_TIMER_NAME = QUALIFIER + ".success";
    private static final String FAILED_TIMER_NAME = QUALIFIER + ".failed";
    private static final String ERROR_TIMER_NAME = QUALIFIER + ".error";

    private MeterRegistry meterRegistry;
    private ReactorInstrument reactorInstrument;

    @BeforeMethod
    public void init() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        initMocks(this);
        meterRegistry = new SimpleMeterRegistry();
    }

    @AfterMethod
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @Test
    public void only_success_timer_should_have_value_greater_than_zero() {
        reactorInstrument = ReactorInstrument.builder().meterRegistry(meterRegistry).metricId(MetricId.builder().name(QUALIFIER).hierarchicalName(QUALIFIER).build()).build();
        Mono.just(1)
                .map(a -> a * 3)
                .transform(reactorInstrument::mono)
                .subscribe();

        assertThat(getTimerTotalTimeByTimerName(SUCCESSFUL_TIMER_NAME), is(greaterThan(0.0)));
        assertThat(getTimerTotalTimeByTimerName(FAILED_TIMER_NAME), is(equalTo(-1.0)));
        assertThat(getTimerTotalTimeByTimerName(ERROR_TIMER_NAME), is(equalTo(-1.0)));
    }

    @Test
    public void failed_timer_should_have_value_greater_than_zero_if_exception_happens() {
        reactorInstrument = ReactorInstrument.builder().meterRegistry(meterRegistry).metricId(MetricId.builder().name(QUALIFIER).hierarchicalName(QUALIFIER).build()).build();
        Mono.just(1)
                .map(a -> a * 3)
                .map(this::exceptionHappens)
                .transform(reactorInstrument::mono)
                .doOnError(e -> LOG.error(e.getMessage()))
                .subscribe();

        assertThat(getTimerTotalTimeByTimerName(SUCCESSFUL_TIMER_NAME), is(equalTo(-1.0)));
        assertThat(getTimerTotalTimeByTimerName(FAILED_TIMER_NAME), is(greaterThan(0.0)));
        assertThat(getTimerTotalTimeByTimerName(ERROR_TIMER_NAME), is(equalTo(-1.0)));
    }

    @Test
    public void error_timer_should_have_value_greater_than_zero_if_exception_decision_maker_returns_false() {
        reactorInstrument = ReactorInstrument.builder().meterRegistry(meterRegistry)
          .metricId(MetricId.builder().name(QUALIFIER).hierarchicalName(QUALIFIER).build()).businessExceptionDecisionMaker(e -> true).build();
        Mono.just(1)
                .map(a -> a * 3)
                .map(this::exceptionHappens)
                .transform(reactorInstrument::mono)
                .doOnError(e -> LOG.error(e.getMessage()))
                .subscribe();

        assertThat(getTimerTotalTimeByTimerName(SUCCESSFUL_TIMER_NAME), is(equalTo(-1.0)));
        assertThat(getTimerTotalTimeByTimerName(FAILED_TIMER_NAME), is(equalTo(-1.0)));
        assertThat(getTimerTotalTimeByTimerName(ERROR_TIMER_NAME), is(greaterThan(0.0)));
    }

    @Test
    public void dimensional_metrics_enabled_should_register_labels_for_status() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        reactorInstrument = ReactorInstrument.builder().meterRegistry(meterRegistry).metricId(MetricId.builder().name(QUALIFIER).hierarchicalName(QUALIFIER).build()).build();
        // When
        Mono.just(1).transform(reactorInstrument::mono).subscribe();
        // Then
        Assertions.assertThat(meterRegistry.get(QUALIFIER).tag("status", "success").tag(GRAPHITE_ID, QUALIFIER + ".success").timer().count()).isEqualTo(1);
    }

    private Integer exceptionHappens(Integer integer) {
        throw new NullPointerException("test error");
    }

    private double getTimerTotalTimeByTimerName(String name) {
        return meterRegistry.getMeters()
                .stream()
                .filter(meter -> meter.getId().getName().equals(name))
                .findFirst()
                .filter(meter -> meter instanceof CumulativeTimer)
                .map(meter -> ((CumulativeTimer) meter).totalTime(TimeUnit.MILLISECONDS))
                .orElse(-1.0);
    }
}
