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
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Unit test for {@link Instrument}.
 */
public class InstrumentTest {
    private static final String QUALIFIER = "qualifier";
    private InstrumentTestFixture fixture;
    private Instrument.Builder instrumentBuilder;

    @BeforeEach
    void setUp() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        fixture = new InstrumentTestFixture();
        instrumentBuilder = Instrument.builder(fixture.getMeterRegistry()).withQualifier("test");
    }

    @AfterEach
    void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @Test
    void should_return_supplier_result_if_success() {
        final String result = "result";
        fixture
            .when(() -> instrumentBuilder.build().supplier(() -> result).get())
            .expectResult(result);
    }

    @Test
    void should_return_function_result_if_success() {
        Function<Integer, Integer> increment = num -> num + 1;
        fixture.when(() -> instrumentBuilder.build().function(increment).apply(1))
            .expectResult(2);
    }

    @Test
    void should_return_callable_result_if_success() {
        final String result = "result";
        fixture.when(() -> instrumentBuilder.build().callable(() -> result).call())
            .expectResult(result);
    }

    @Test
    void should_return_consumer_result_if_success() {
        List<Integer> list = new ArrayList<>();
        IntConsumer c = list::add;
        Consumer<Integer> consumer = instrumentBuilder.build().consumer(c::accept);

        fixture.when(() -> {
            consumer.accept(2);
            return null;
        })
            .expectResult(null);
        assertThat(list, is(List.of(2)));
    }

    @Test
    void should_run_runnable() {
        AtomicBoolean called = new AtomicBoolean(false);
        Runnable runnable = instrumentBuilder.build().runnable(() -> called.set(true));

        fixture.when(() -> {
            runnable.run();
            return null;
        })
            .expectResult(null);
        assertThat(called.get(), is(true));
    }

    @Test
    void should_throw_original_exception_from_runnable() {
        RuntimeException expectedException = new RuntimeException();
        Runnable runnable = () -> {
            throw expectedException;
        };
        fixture.when(() -> {
            instrumentBuilder.build().runnable(runnable).run();
            return null;
        })
            .expectThrowNonBusinessException(expectedException);
    }

    @Test
    void should_throw_original_exception_from_function() {
        RuntimeException expectedException = new RuntimeException();
        Function<Integer, Integer> function = num -> {
            throw expectedException;
        };
        fixture
            .when(() -> instrumentBuilder.build().function(function).apply(1))
            .expectThrowNonBusinessException(expectedException);
    }

    @Test
    void should_throw_original_exception_from_supplier() {
        RuntimeException expectedException = new RuntimeException();
        Supplier<Integer> supplier = () -> {
            throw expectedException;
        };
        fixture.when(() -> instrumentBuilder.build().supplier(supplier).get())
            .expectThrowNonBusinessException(expectedException);
    }

    @Test
    void should_throw_original_exception_from_callable() {
        RuntimeException expectedException = new RuntimeException();
        Callable<Integer> callable = () -> {
            throw expectedException;
        };
        fixture.when(() -> instrumentBuilder.build().callable(callable).call())
            .expectThrowNonBusinessException(expectedException);
    }

    @Test
    void should_throw_original_exception_from_consumer() {
        RuntimeException expectedException = new RuntimeException();
        Consumer<Integer> consumer = instrumentBuilder.build().consumer(integer -> {
            throw expectedException;
        });

        fixture.when(() -> {
            consumer.accept(2);
            return null;
        })
            .expectThrowNonBusinessException(expectedException);
    }

    @Test
    void should_function_throw_business_exception() {
        RuntimeException expectedException = new RuntimeException();
        Function<Integer, Integer> function = num -> {
            throw expectedException;
        };
        fixture.when(() -> instrumentBuilder.withBusinessExceptionDecisionMaker(e -> true).build().function(function).apply(1))
            .expectThrowBusinessException(expectedException);
    }

    @Test
    void should_supplier_throw_business_exception() {
        RuntimeException expectedException = new RuntimeException();
        Supplier<Integer> supplier = () -> {
            throw expectedException;
        };
        fixture.when(() -> instrumentBuilder.withBusinessExceptionDecisionMaker(e -> true).build().supplier(supplier).get())
            .expectThrowBusinessException(expectedException);
    }

    @Test
    void should_callable_throw_business_exception() {
        RuntimeException expectedException = new RuntimeException();
        Callable<Integer> callable = () -> {
            throw expectedException;
        };
        fixture.when(() -> instrumentBuilder.withBusinessExceptionDecisionMaker(e -> true).build().callable(callable).call())
            .expectThrowBusinessException(expectedException);
    }

    @Test
    void should_consumer_throw_business_exception() {
        RuntimeException expectedException = new RuntimeException();
        Consumer<Integer> consumer = instrumentBuilder.withBusinessExceptionDecisionMaker(e -> true).build().consumer(integer -> {
            throw expectedException;
        });

        fixture.when(() -> {
            consumer.accept(2);
            return null;
        })
            .expectThrowBusinessException(expectedException);
    }

    @Test
    void should_runnable_throw_business_exception() {
        RuntimeException expectedException = new RuntimeException();
        Runnable runnable = instrumentBuilder.withBusinessExceptionDecisionMaker(e -> true).build().runnable(() -> {
            throw expectedException;
        });

        fixture.when(() -> {
            runnable.run();
            return null;
        })
            .expectThrowBusinessException(expectedException);
    }

    @Test
    void dimensional_metrics_enabled_should_register_labels_for_status() throws Exception {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        var meterRegistry = new SimpleMeterRegistry();
        // When
        Instrument.builder(meterRegistry).withQualifier(QUALIFIER).build().call(() -> "good");
        // Then
        Assertions.assertThat(meterRegistry.get(QUALIFIER).tag("status", "success").tag(GRAPHITE_ID, QUALIFIER + ".success").timer().count()).isEqualTo(1);
    }
}
