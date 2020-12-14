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
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * The configuration for recovery parameters.
 * Failure rate determines when a circuit is considered unhealthy and should be opened.
 * For example, failureRate=25 means if 25% of recent calls have failed then the circuit should be opened.
 * Responsiveness affects the maximum time to react to failures with average request rates.
 * See {@link RetrofitServiceClientBuilder} for details.
 */
@EqualsAndHashCode
@ToString
public final class RecoveryConfiguration {
    @Getter
    private final float failureThreshold;
    @Getter
    private final Duration responsiveness;
    private final Integer numberOfCallsToConsiderForHealthyCircuit;
    private final Integer numberOfCallsToConsiderForRecoveringCircuit;
    @Getter
    private final Float slowCallDurationThreshold;
    @Getter
    private final Float slowCallRateThreshold;
    @Getter
    private final RecoveryMode recoveryMode;

    private RecoveryConfiguration(Builder builder) {
        failureThreshold = builder.failureThreshold;
        responsiveness = builder.responsiveness;
        recoveryMode = builder.recoveryMode;
        numberOfCallsToConsiderForHealthyCircuit = builder.numberOfCallsToConsiderForHealthyCircuit;
        numberOfCallsToConsiderForRecoveringCircuit = builder.numberOfCallsToConsiderForRecoveringCircuit;
        slowCallDurationThreshold = builder.slowCallDurationThreshold;
        slowCallRateThreshold = builder.slowCallRateThreshold;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Integer> getNumberOfCallsToConsiderForHealthyCircuit() {
        return Optional.ofNullable(numberOfCallsToConsiderForHealthyCircuit);
    }

    public Optional<Integer> getNumberOfCallsToConsiderForRecoveringCircuit() {
        return Optional.ofNullable(numberOfCallsToConsiderForRecoveringCircuit);
    }

    /**
     * Builder for {@link RecoveryConfiguration}.
     */
    public static final class Builder {
        private float failureThreshold = 25F;
        private Duration responsiveness = Duration.ofSeconds(30);
        private Integer numberOfCallsToConsiderForHealthyCircuit;
        private Integer numberOfCallsToConsiderForRecoveringCircuit;
        private Float slowCallDurationThreshold = 100F;
        private Float slowCallRateThreshold = 100F;
        private RecoveryMode recoveryMode = RecoveryMode.TIME_BASED;

        private Builder() {
        }

        /**
         * Sets the failure threshold in percentage {@code (0, 100]}.
         * If the rate of failing calls is equal to or greater than the threshold the circuit opens.
         * Default is 25%.
         *
         * @param val the failure threshold after which the circuit should be opened
         * @return this builder instance
         */
        public Builder failureThreshold(float val) {
            checkArgument(val > 0F && val <= 100F, "The failure threshold must be in (0, 100] but was " + val);
            failureThreshold = val;
            return this;
        }

        /**
         * Sets the responsiveness. The circuit will try to react to failures and recover from failures based on this value.
         * For example if this is set to 5 seconds, then it will monitor the last 5 seconds of calls and if failure rate is above threshold it will open
         * the circuit for 5 seconds.
         * For count based recovery the monitoring of last x seconds is approximated based on the expected average request rate.
         * Default is 30 seconds.
         *
         * @param val the responsiveness
         * @return this builder instance
         */
        public Builder responsiveness(Duration val) {
            checkArgument(val.getSeconds() > 0, "The responsiveness must be at least 1 second but was " + val);
            responsiveness = val;
            return this;
        }

        /**
         * Sets recovery mode. Default is time based.
         *
         * @return this builder instance
         */
        public Builder recoveryMode(RecoveryMode mode) {
            this.recoveryMode = requireNonNull(mode);
            return this;
        }

        /**
         * Enables time based recovery. This is the default.
         *
         * @return this builder instance
         */
        public Builder timeBasedRecovery() {
            return recoveryMode(RecoveryMode.TIME_BASED);
        }

        /**
         * Enables count based recovery.
         *
         * @return this builder instance
         */
        public Builder countBasedRecovery() {
            return recoveryMode(RecoveryMode.COUNT_BASED);
        }

        /**
         * Overrides the calculated number of calls to consider for healthy circuit status.
         * At least this amount of calls needs to complete before failure rate starts to affect circuit state.
         * For count based recovery this is also the number of recent calls to consider for current circuit health.
         * By default this is based on the average request rate and the responsiveness: {@code [average request rate] * [responsiveness in seconds]}
         * but not less than 20.
         *
         * @param val the number of calls to consider
         * @return this builder instance
         */
        public Builder numberOfCallsToConsiderForHealthyCircuit(int val) {
            checkArgument(val > 0, "The number of calls to consider for healthy circuit must be positive but was " + val);
            numberOfCallsToConsiderForHealthyCircuit = val;
            return this;
        }

        /**
         * Overrides the calculated number of calls to consider for recovering circuit status.
         * This is the number of calls which will be permitted to reevaluate the state of the circuit.
         * By default this is the peak request rate but not more than 10.
         *
         * @param val the number of calls to consider
         * @return this builder instance
         */
        public Builder numberOfCallsToConsiderForRecoveringCircuit(int val) {
            checkArgument(val > 0, "The number of calls to consider for recovering circuit must be positive but was " + val);
            numberOfCallsToConsiderForRecoveringCircuit = val;
            return this;
        }

        /**
         * Sets a slow call duration threshold as percentage {@code (0,100]} based on the expected peak response times.
         * Calls which are slower than {@code [peak response time] * [slow call duration threshold] / 100} are count as slow call and increase the slow calls percentage.
         * Default is 100%.
         *
         * @param val the slow calls duration threshold in percentage
         * @return this builder instance
         */
        public Builder slowCallDurationThreshold(float val) {
            checkArgument(val > 0F && val <= 100F, "The slow call duration threshold must be in (0, 100] but was " + val);
            slowCallDurationThreshold = val;
            return this;
        }

        /**
         * Sets a slow call rate threshold as the percentage of all calls {@code (0,100]}.
         * When the percentage of slow calls is equal to or greater than the threshold the circuit will open.
         * Default is 100%.
         *
         * @param val the slow calls rate threshold in percentage
         * @return this builder instance
         */
        public Builder slowCallRateThreshold(float val) {
            checkArgument(val > 0F && val <= 100F, "The slow call rate threshold must be in (0, 100] but was " + val);
            slowCallRateThreshold = val;
            return this;
        }

        public RecoveryConfiguration build() {
            return new RecoveryConfiguration(this);
        }
    }

    /**
     * The possible recovery modes supported.
     */
    public enum RecoveryMode {
        /**
         * Uses exact time window to evaluate circuit health.
         */
        TIME_BASED,
        /**
         * Uses count based approximation of time window based on expected load for circuit health evaluation.
         */
        COUNT_BASED
    }
}
