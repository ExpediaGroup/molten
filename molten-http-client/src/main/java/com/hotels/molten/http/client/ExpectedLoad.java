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

package com.hotels.molten.http.client;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.time.Duration;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Defines metrics for expected service load.
 * See {@link RetrofitServiceClientBuilder} for details on how it affects timeouts and capacities.
 */
@EqualsAndHashCode
@ToString
@Getter
public final class ExpectedLoad {
    private final Duration peakResponseTime;
    private final Duration averageResponseTime;
    private final int peakRequestRatePerSecond;
    private final int averageRequestRatePerSecond;

    private ExpectedLoad(Builder builder) {
        peakResponseTime = builder.peakResponseTime;
        averageResponseTime = builder.averageResponseTime;
        peakRequestRatePerSecond = builder.peakRequestRatePerSecond;
        averageRequestRatePerSecond = builder.averageRequestRatePerSecond;
    }

    /**
     * Creates an {@link ExpectedLoad} builder with defaults.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ExpectedLoad}.
     * Defaults are 1000 ms peak response time, 100 ms average response time, 10 requests per sec peak rate and 5 requests per sec average rate.
     */
    public static final class Builder {
        private Duration peakResponseTime = Duration.ofSeconds(1);
        private Duration averageResponseTime = Duration.ofMillis(100);
        private int peakRequestRatePerSecond = 10;
        private int averageRequestRatePerSecond = 5;

        private Builder() {
        }

        /**
         * Sets the expected peak response times.
         *
         * @param val the expected peak response time
         * @return this builder
         */
        public Builder peakResponseTime(Duration val) {
            checkArgument(val.toMillis() > 0, "Peak response time must be positive");
            peakResponseTime = val;
            return this;
        }

        /**
         * Sets the expected average response times.
         *
         * @param val the expected average response time
         * @return this builder
         */
        public Builder averageResponseTime(Duration val) {
            checkArgument(val.toMillis() > 0, "Average response time must be positive");
            averageResponseTime = val;
            return this;
        }

        /**
         * Sets the expected peak request rate.
         *
         * @param val the expected peak number of requests per seconds
         * @return this builder
         */
        public Builder peakRequestRatePerSecond(int val) {
            checkArgument(val > 0, "Peak request rate must be positive");
            peakRequestRatePerSecond = val;
            return this;
        }

        /**
         * Sets the expected average request rate.
         *
         * @param val the expected average number of requests per seconds
         * @return this builder
         */
        public Builder averageRequestRatePerSecond(int val) {
            checkArgument(val > 0, "Average request rate must be positive");
            averageRequestRatePerSecond = val;
            return this;
        }

        /**
         * Builds the {@link ExpectedLoad}.
         *
         * @return expected load settings
         */
        public ExpectedLoad build() {
            checkState(peakResponseTime.toMillis() > averageResponseTime.toMillis(), "Peak response time must be higher than average response time");
            return new ExpectedLoad(this);
        }
    }
}
