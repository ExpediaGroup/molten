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

import static com.hotels.molten.core.metrics.MetricsSupport.GRAPHITE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Unit test for {@link RetrofitServiceClientConfigurationReporter}.
 */
public class RetrofitServiceClientConfigurationReporterTest {
    private static final String CLIENT_ID = "clientId";
    private MeterRegistry meterRegistry;
    private RetrofitServiceClientConfigurationReporter reporter;

    @BeforeMethod
    public void initContext() {
        meterRegistry = new SimpleMeterRegistry();
        reporter = new RetrofitServiceClientConfigurationReporter(meterRegistry, CLIENT_ID);
    }

    @AfterMethod
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @Test
    public void should_register_gauge_for_each_configuration_value_with_hierarchical_names() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);

        reporter.registerMetrics(createConfig());

        assertHierarchicalGaugeFor("connectionTimeoutInMs", 500D);
        assertHierarchicalGaugeFor("connectionKeepAliveInSec", 10D);
        assertHierarchicalGaugeFor("connectionMaxLifeInSec", 60D);
        assertHierarchicalGaugeFor("retryOnConnectionFailure", 1D);
        assertHierarchicalGaugeFor("peakTimeInMs", 400D);
        assertHierarchicalGaugeFor("averageTimeInMs", 150D);
        assertHierarchicalGaugeFor("peakRatePerSec", 15D);
        assertHierarchicalGaugeFor("averageRatePerSec", 3D);
        assertHierarchicalGaugeFor("totalTimeoutInMs", 950D);
        assertHierarchicalGaugeFor("retries", 2D);
        assertHierarchicalGaugeFor("concurrency", 15D);
        assertHierarchicalGaugeFor("responsivenessInSec", 8D);
        assertHierarchicalGaugeFor("recoveryMode", 1D);
        assertHierarchicalGaugeFor("slowCallDurationThresholdPercentage", 75D);
        assertHierarchicalGaugeFor("slowCallRateThresholdPercentage", 55D);
    }

    @Test
    public void should_register_gauge_for_each_configuration_value_with_dimensional_name_and_labels() {
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);

        reporter.registerMetrics(createConfig());

        assertDimensionalGaugeFor("connectionTimeoutInMs", 500D);
        assertDimensionalGaugeFor("connectionKeepAliveInSec", 10D);
        assertDimensionalGaugeFor("connectionMaxLifeInSec", 60D);
        assertDimensionalGaugeFor("retryOnConnectionFailure", 1D);
        assertDimensionalGaugeFor("peakTimeInMs", 400D);
        assertDimensionalGaugeFor("averageTimeInMs", 150D);
        assertDimensionalGaugeFor("peakRatePerSec", 15D);
        assertDimensionalGaugeFor("averageRatePerSec", 3D);
        assertDimensionalGaugeFor("totalTimeoutInMs", 950D);
        assertDimensionalGaugeFor("retries", 2D);
        assertDimensionalGaugeFor("concurrency", 15D);
        assertDimensionalGaugeFor("responsivenessInSec", 8D);
        assertDimensionalGaugeFor("recoveryMode", 1D);
        assertDimensionalGaugeFor("slowCallDurationThresholdPercentage", 75D);
        assertDimensionalGaugeFor("slowCallRateThresholdPercentage", 55D);
    }

    private RetrofitServiceClientConfiguration createConfig() {
        var config = new RetrofitServiceClientConfiguration<>(ServiceEndpoint.class);
        config.setConnectionSettings(ConnectionSettings.builder()
            .timeout(Duration.ofMillis(500))
            .keepAliveIdle(Duration.ofSeconds(10))
            .maxLife(Duration.ofSeconds(60))
            .retryOnConnectionFailure(true)
            .build());
        config.setExpectedLoad(ExpectedLoad.builder()
            .peakResponseTime(Duration.ofMillis(400))
            .peakRequestRatePerSecond(15)
            .averageResponseTime(Duration.ofMillis(150))
            .averageRequestRatePerSecond(3)
            .build());
        config.setRecoveryConfiguration(RecoveryConfiguration.builder()
            .responsiveness(Duration.ofSeconds(8))
            .failureThreshold(33F)
            .slowCallDurationThreshold(75F)
            .slowCallRateThreshold(55F)
            .recoveryMode(RecoveryConfiguration.RecoveryMode.COUNT_BASED)
            .build());
        config.setRetries(2);
        return config;
    }

    private void assertHierarchicalGaugeFor(String name, Object expectedValue) {
        Gauge gauge = meterRegistry.get("client.clientId.config." + name).gauge();
        assertThat(gauge.value()).isEqualTo(expectedValue);
    }

    private void assertDimensionalGaugeFor(String name, Object expectedValue) {
        Gauge gauge = meterRegistry.get("http_client_configuration")
            .tag("client", CLIENT_ID)
            .tag("config", name)
            .tag(GRAPHITE_ID, "client." + CLIENT_ID + ".config." + name).gauge();
        assertThat(gauge.value()).isEqualTo(expectedValue);
    }
}
