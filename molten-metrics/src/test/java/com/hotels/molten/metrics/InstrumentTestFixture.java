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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.Is.is;
import static org.testng.Assert.fail;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.Data;
import org.hamcrest.Matchers;

/**
 * Fixture for metrics instrumentation tests.
 */
@Data
public class InstrumentTestFixture {
    private static final String QUALIFIER = "test";
    private static final String SUCCESSFUL_TIMER_NAME = QUALIFIER + ".success";
    private static final String FAILED_TIMER_NAME = QUALIFIER + ".failed";
    private static final String ERROR_TIMER_NAME = QUALIFIER + ".error";

    private final MeterRegistry meterRegistry;

    public InstrumentTestFixture() {
        meterRegistry = new SimpleMeterRegistry();
    }

    /**
     * Verifies that only success timer has been stopped.
     */
    public void verifySuccess() {
        assertThat(getTimerTotalTimeByTimerName(SUCCESSFUL_TIMER_NAME), Matchers.is(greaterThan(0.0)));
        assertThat(getTimerTotalTimeByTimerName(FAILED_TIMER_NAME), Matchers.is(equalTo(-1.0)));
        assertThat(getTimerTotalTimeByTimerName(ERROR_TIMER_NAME), Matchers.is(equalTo(-1.0)));
    }

    /**
     * Verifies that only failure timer has been stopped.
     */
    public void verifyFailure() {
        assertThat(getTimerTotalTimeByTimerName(SUCCESSFUL_TIMER_NAME), Matchers.is(equalTo(-1.0)));
        assertThat(getTimerTotalTimeByTimerName(FAILED_TIMER_NAME), Matchers.is(greaterThan(0.0)));
        assertThat(getTimerTotalTimeByTimerName(ERROR_TIMER_NAME), Matchers.is(equalTo(-1.0)));
    }

    /**
     * Verifies that only error timer has been stopped.
     */
    public void verifyError() {
        assertThat(getTimerTotalTimeByTimerName(SUCCESSFUL_TIMER_NAME), Matchers.is(equalTo(-1.0)));
        assertThat(getTimerTotalTimeByTimerName(FAILED_TIMER_NAME), Matchers.is(equalTo(-1.0)));
        assertThat(getTimerTotalTimeByTimerName(ERROR_TIMER_NAME), Matchers.is(greaterThan(0.0)));
    }

    /**
     * In this step the tested method call can be fired.
     *
     * @param supplier which calls the method
     * @param <T> the type of the return value of the tested method
     * @return a step where the expected behaviour can be defined
     */
    public <T> When<T> when(CheckedSupplier<T> supplier) {
        return new When<>(supplier);
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

    /**
     * Provides the ability to define the expected behaviour and result.
     *
     * @param <T> the type of the return value of the tested method
     */
    public final class When<T> {

        private CheckedSupplier<T> supplier;

        private When(CheckedSupplier<T> supplier) {
            this.supplier = supplier;
        }

        /**
         * The method must return the expected value and most not throw any {@link Exception}.
         *
         * @param expectedResult the result
         * @param <R> type of the expected result
         */
        public <R extends T> void expectResult(R expectedResult) {
            try {
                assertThat(supplier.get(), is(expectedResult));
                verifySuccess();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        /**
         * A business exception must be thrown.
         *
         * @param expectedException the exception
         */
        public void expectThrowNonBusinessException(Exception expectedException) {
            try {
                supplier.get();
                fail("Business exception should have been thrown");
            } catch (Throwable throwable) {
                assertThat(throwable, is(expectedException));
            }
            verifyFailure();
        }

        /**
         * A non-business exception must be thrown.
         *
         * @param expectedException the exception
         */
        public void expectThrowBusinessException(Exception expectedException) {
            try {
                supplier.get();
                fail("Non-Business exception should have been thrown");
            } catch (Throwable throwable) {
                assertThat(throwable, is(expectedException));
            }
            verifyError();
        }

    }

    /**
     * Similar to {@link java.util.function.Supplier} but can throw any {@link Exception}s.
     *
     * @param <T> Result type of this supplier
     */
    public interface CheckedSupplier<T> {

        /**
         * Gets the result.
         *
         * @return a result
         * @throws Exception thrown be the implementation
         */
        T get() throws Exception;
    }
}
