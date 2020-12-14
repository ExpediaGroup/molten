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

import static com.hotels.molten.core.metrics.MetricsSupport.name;

import java.time.Duration;
import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import com.hotels.molten.core.metrics.MetricId;

/**
 * Exposes settings of client built with {@link RetrofitServiceClientBuilder} via {@link MeterRegistry}.
 */
@RequiredArgsConstructor
class RetrofitServiceClientConfigurationReporter {
    @NonNull
    private final MeterRegistry meterRegistry;
    @NonNull
    private final String clientId;

    void registerMetrics(RetrofitServiceClientConfiguration configuration) {
        var metricId = MetricId.builder()
            .name("http_client_configuration")
            .hierarchicalName(name("client", clientId, "config"))
            .tag(Tag.of("client", clientId))
            .build();
        gaugeFor(metricId, "connectionTimeoutInMs", configuration.getConnectionSettings(), c -> c.getTimeout().toMillis());
        gaugeFor(metricId, "connectionKeepAliveInSec", configuration.getConnectionSettings(), c -> c.getKeepAliveIdle().toSeconds());
        gaugeFor(metricId, "connectionMaxLifeInSec", configuration.getConnectionSettings(), c -> c.getMaxLife().toSeconds());
        gaugeFor(metricId, "retryOnConnectionFailure", configuration.getConnectionSettings(), c -> c.isRetryOnConnectionFailure() ? 1 : 0);
        gaugeFor(metricId, "peakTimeInMs", configuration.getExpectedLoad(), e -> e.getPeakResponseTime().toMillis());
        gaugeFor(metricId, "averageTimeInMs", configuration.getExpectedLoad(), e -> e.getAverageResponseTime().toMillis());
        gaugeFor(metricId, "peakRatePerSec", configuration.getExpectedLoad(), ExpectedLoad::getPeakRequestRatePerSecond);
        gaugeFor(metricId, "averageRatePerSec", configuration.getExpectedLoad(), ExpectedLoad::getAverageRequestRatePerSecond);
        gaugeFor(metricId, "totalTimeoutInMs", configuration, RetrofitServiceClientConfiguration::getTotalTimeoutInMs);
        gaugeFor(metricId, "retries", configuration, RetrofitServiceClientConfiguration::getRetries);
        gaugeFor(metricId, "concurrency", configuration, RetrofitServiceClientConfiguration::getConcurrency);
        gaugeFor(metricId, "responsivenessInSec", configuration.getRecoveryConfiguration().getResponsiveness(), Duration::getSeconds);
        gaugeFor(metricId, "recoveryMode", configuration.getRecoveryConfiguration().getRecoveryMode(), Enum::ordinal);
        gaugeFor(metricId, "failureThresholdPercentage", configuration.getRecoveryConfiguration(), RecoveryConfiguration::getFailureThreshold);
        gaugeFor(metricId, "slowCallDurationThresholdPercentage", configuration.getRecoveryConfiguration(), RecoveryConfiguration::getSlowCallDurationThreshold);
        gaugeFor(metricId, "slowCallRateThresholdPercentage", configuration.getRecoveryConfiguration(), RecoveryConfiguration::getSlowCallRateThreshold);
    }

    private <T> void gaugeFor(MetricId metricId, String name, T target, ToDoubleFunction<T> valueFunction) {
        metricId.tagWith(Tag.of("config", name)).toGauge(target, valueFunction).register(meterRegistry);
    }
}
