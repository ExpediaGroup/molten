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
import static com.hotels.molten.http.client.RetrofitServiceClientBuilder.SYSTEM_PROP_PREFIX;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.List;

import brave.http.HttpTracing;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

import com.hotels.molten.http.client.tracking.RequestTracking;

/**
 * Holds configuration for a {@link RetrofitServiceClientBuilder}. Also calculates derived configuration based on settings.
 */
@Getter
@EqualsAndHashCode
@ToString
class RetrofitServiceClientConfiguration<API> {
    private static final ExpectedLoad DEFAULT_EXPECTED_LOAD = ExpectedLoad.builder()
        .averageResponseTime(Duration.ofMillis(100))
        .averageRequestRatePerSecond(5)
        .peakResponseTime(Duration.ofSeconds(1))
        .peakRequestRatePerSecond(10)
        .build();
    private static final RecoveryConfiguration DEFAULT_RECOVERY_CONFIGURATION = RecoveryConfiguration.builder()
        .failureThreshold(25F)
        .responsiveness(Duration.ofSeconds(30))
        .build();
    private static final int DEFAULT_NUMBER_OF_RETRIES = 0;
    private static final int MINIMUM_NUMBER_OF_CALLS_TO_CONSIDER_FOR_CLOSED_CIRCUIT_BREAKER_STATUS = 20;
    private static final int MAXIMUM_NUMBER_OF_CALLS_TO_CONSIDER_FOR_HALF_OPEN_CIRCUIT_BREAKER_STATUS = 10;
    private static final ConnectionSettings DEFAULT_CONNECTION_SETTINGS = ConnectionSettings.builder()
        .timeout(Duration.ofSeconds(1))
        .keepAliveIdle(Duration.ofMinutes(15))
        .retryOnConnectionFailure(true)
        .build();

    @NonNull
    private final Class<API> api;
    private ExpectedLoad expectedLoad = DEFAULT_EXPECTED_LOAD;
    private RecoveryConfiguration recoveryConfiguration = DEFAULT_RECOVERY_CONFIGURATION;
    private int retries = DEFAULT_NUMBER_OF_RETRIES;
    private ConnectionSettings connectionSettings = DEFAULT_CONNECTION_SETTINGS;
    private List<Protocols> protocol;
    @Setter
    @NonNull
    private RequestTracking requestTracking = RequestTracking.builder().build();
    @Setter
    private SSLContextConfiguration sslContextConfiguration;
    @Setter
    private HttpTracing httpTracing;
    @Setter
    private MeterRegistry meterRegistry;
    @Setter
    private boolean logHttpEvents;
    @Setter
    private boolean reportHttpEvents;

    RetrofitServiceClientConfiguration(@NonNull Class<API> api) {
        this.api = requireNonNull(api);
    }

    void setExpectedLoad(ExpectedLoad expectedLoad) {
        checkArgument(expectedLoad != null, "Non-null configuration must be set.");
        this.expectedLoad = expectedLoad;
    }

    void setRecoveryConfiguration(RecoveryConfiguration recoveryConfiguration) {
        checkArgument(recoveryConfiguration != null, "Non-null configuration must be set.");
        this.recoveryConfiguration = recoveryConfiguration;
    }

    void setRetries(int retries) {
        checkArgument(retries >= 0, "Number of retries must be non-negative.");
        this.retries = retries;
    }

    void setConnectionSettings(ConnectionSettings connectionSettings) {
        checkArgument(connectionSettings != null, "Non-null connection settings must be set");
        this.connectionSettings = connectionSettings;
    }

    void setProtocol(List<Protocols> protocol) {
        checkArgument(protocol != null, "Non-null protocol settings must be set");
        this.protocol = protocol;
    }

    /**
     * Calculates the total timeout for this configuration considering avg/peak response times and number of retries.
     * If there are retries the total timeout considers the very last request at average response time.
     * e.g. with 2 retries, 100ms average and 1 sec peak response time the total timeout will be 2100 ms.
     * On the other hand, when there are no retries (set to 0) the total timeout will be the peak response time.
     *
     * @return the total timeout
     */
    long getTotalTimeoutInMs() {
        long total = expectedLoad.getPeakResponseTime().toMillis();
        if (retries > 0) {
            total = expectedLoad.getPeakResponseTime().toMillis() * retries + expectedLoad.getAverageResponseTime().toMillis();
        }
        return total;
    }

    /**
     * Calculates the maximum concurrency for this configuration considering total timeout and peak request rate.
     * e.g. if the total timeout is 2100ms and peak request rate is 3 then concurrency will be 7.
     *
     * @return the concurrency (minimum 1)
     * @see #getTotalTimeoutInMs()
     */
    int getConcurrency() {
        return (int) Math.ceil((double) getTotalTimeoutInMs() * (double) expectedLoad.getPeakRequestRatePerSecond() / 1000D);
    }

    /**
     * Calculates the calls to consider for closed circuit health.
     *
     * @return the number of calls
     */
    int getHealthyCircuitCallsToConsider() {
        return recoveryConfiguration.getNumberOfCallsToConsiderForHealthyCircuit()
            .orElse(Math.max(MINIMUM_NUMBER_OF_CALLS_TO_CONSIDER_FOR_CLOSED_CIRCUIT_BREAKER_STATUS,
                (int) recoveryConfiguration.getResponsiveness().getSeconds() * expectedLoad.getAverageRequestRatePerSecond()));
    }

    /**
     * Calculates the calls to consider for half open circuit health.
     *
     * @return the number of calls
     */
    int getRecoveringCircuitCallsToConsider() {
        return recoveryConfiguration.getNumberOfCallsToConsiderForRecoveringCircuit()
            .orElse(Math.min(MAXIMUM_NUMBER_OF_CALLS_TO_CONSIDER_FOR_HALF_OPEN_CIRCUIT_BREAKER_STATUS, expectedLoad.getPeakRequestRatePerSecond()));
    }

    /**
     * Calculates the slow call durtion based on its threshold and peak response time.
     * e.g. if peak response time is 2 seconds and slow call duration is 75% then this will return 1500ms
     *
     * @return the threshold for slow calls as duration
     */
    Duration getSlowCallDurationThreshold() {
        int slowCallThresholdInMs = (int) Math.floor((double) expectedLoad.getPeakResponseTime().toMillis() * (double) recoveryConfiguration.getSlowCallDurationThreshold() / 100D);
        return Duration.ofMillis(slowCallThresholdInMs);
    }

    boolean isReportingHttpEventsEnabled() {
        return reportHttpEvents || "true".equalsIgnoreCase(System.getProperty(SYSTEM_PROP_PREFIX + "REPORT_TRACE_" + getNormalizedCanonicalApi()));
    }

    boolean isLoggingHttpEventsEnabled() {
        return logHttpEvents || "true".equalsIgnoreCase(System.getProperty(SYSTEM_PROP_PREFIX + "LOG_TRACE_" + getNormalizedCanonicalApi()));
    }

    boolean isLogDetailedHttpEventsEnabled() {
        return "true".equalsIgnoreCase(System.getProperty(SYSTEM_PROP_PREFIX + "LOG_DETAILED_TRACE_" + getNormalizedCanonicalApi()));
    }

    String getNormalizedCanonicalApi() {
        return api.getCanonicalName().replaceAll("\\.", "_");
    }
}
