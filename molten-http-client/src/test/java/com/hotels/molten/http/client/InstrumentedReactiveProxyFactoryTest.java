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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hotels.molten.core.metrics.MoltenMetrics;
import com.hotels.molten.test.mockito.ReactiveMock;
import com.hotels.molten.test.mockito.ReactiveMockitoAnnotations;

/**
 * Unit test for {@link InstrumentedReactiveProxyFactory}.
 */
public class InstrumentedReactiveProxyFactoryTest {
    private static final String CLIENT_ID = "clientId";
    private InstrumentedReactiveProxyFactory proxyFactory;
    @ReactiveMock
    private Camoo service;
    private MockClock clock;
    private MeterRegistry meterRegistry;

    @BeforeMethod
    public void initContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        ReactiveMockitoAnnotations.initMocks(this);
        clock = new MockClock();
        meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        proxyFactory = new InstrumentedReactiveProxyFactory(meterRegistry, CLIENT_ID, "raw");
    }

    @AfterMethod
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @Test
    public void should_delegate_call_to_actual_service() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.just(Camoo.RESULT));

        StepVerifier.create(proxyFactory.wrap(Camoo.SERVICE_TYPE, service).get(Camoo.PARAM)).expectNext(Camoo.RESULT).verifyComplete();

        verify(service, times(1)).get(Camoo.PARAM);
    }

    @Test
    public void should_report_success() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.defer(() -> Mono.just(Camoo.RESULT).delayElement(Duration.ofMillis(50))));

        StepVerifier.withVirtualTime(() -> proxyFactory.wrap(Camoo.SERVICE_TYPE, service).get(Camoo.PARAM))
            .then(() -> assertThat(hierarchicalTotalTimeMetricOf("get")).isEqualTo(0D))
            .then(() -> assertThat(hierarchicalCountMetricOf("get", "success")).isEqualTo(0L))
            .then(() -> clock.add(Duration.ofMillis(50)))
            .thenAwait(Duration.ofMillis(50))
            .expectNext(Camoo.RESULT)
            .then(() -> assertThat(hierarchicalTotalTimeMetricOf("get")).isEqualTo(50D))
            .then(() -> assertThat(hierarchicalCountMetricOf("get", "success")).isEqualTo(1L))
            .verifyComplete();
    }

    @Test
    public void should_report_per_method() {
        when(service.anotherGet(Camoo.PARAM)).thenReturn(Mono.defer(() -> Mono.just(Camoo.RESULT).delayElement(Duration.ofMillis(50))));

        StepVerifier.withVirtualTime(() -> proxyFactory.wrap(Camoo.SERVICE_TYPE, service).anotherGet(Camoo.PARAM))
            .then(() -> assertThat(hierarchicalTotalTimeMetricOf("anotherGet")).isEqualTo(0D))
            .then(() -> assertThat(hierarchicalCountMetricOf("anotherGet", "success")).isEqualTo(0L))
            .then(() -> clock.add(Duration.ofMillis(50)))
            .thenAwait(Duration.ofMillis(50))
            .expectNext(Camoo.RESULT)
            .then(() -> assertThat(hierarchicalTotalTimeMetricOf("anotherGet")).isEqualTo(50D))
            .then(() -> assertThat(hierarchicalCountMetricOf("anotherGet", "success")).isEqualTo(1L))
            .verifyComplete();
    }

    @Test
    public void should_report_failure_for_temporary_service_invocation_exception() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(new TemporaryServiceInvocationException("doh", service.getClass(), new NullPointerException())));

        assertThat(hierarchicalCountMetricOf("get", "failure")).isEqualTo(0L);
        StepVerifier.withVirtualTime(() -> proxyFactory.wrap(Camoo.SERVICE_TYPE, service).get(Camoo.PARAM))
            .verifyError(TemporaryServiceInvocationException.class);
        assertThat(hierarchicalCountMetricOf("get", "failure")).isEqualTo(1L);
    }

    @Test
    public void should_report_error_for_permanent_service_invocation_exception() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(new PermanentServiceInvocationException("doh", service.getClass(), new NullPointerException())));

        assertThat(hierarchicalCountMetricOf("get", "error")).isEqualTo(0L);
        StepVerifier.withVirtualTime(() -> proxyFactory.wrap(Camoo.SERVICE_TYPE, service).get(Camoo.PARAM))
            .verifyError(PermanentServiceInvocationException.class);
        assertThat(hierarchicalCountMetricOf("get", "error")).isEqualTo(1L);
    }

    @Test
    public void should_report_dimensional_metrics_when_enabled() {
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);

        when(service.get(Camoo.PARAM)).thenReturn(Mono.defer(() -> Mono.just(Camoo.RESULT).delayElement(Duration.ofMillis(50))));

        StepVerifier.withVirtualTime(() -> proxyFactory.wrap(Camoo.SERVICE_TYPE, service).get(Camoo.PARAM))
            .then(() -> assertThat(totalDimensionalTimeMetricOf("get")).isEqualTo(0D))
            .then(() -> assertThat(countDimensionalMetricOf("get", "success")).isEqualTo(0L))
            .then(() -> clock.add(Duration.ofMillis(50)))
            .thenAwait(Duration.ofMillis(50))
            .expectNext(Camoo.RESULT)
            .then(() -> assertThat(totalDimensionalTimeMetricOf("get")).isEqualTo(50D))
            .then(() -> assertThat(countDimensionalMetricOf("get", "success")).isEqualTo(1L))
            .verifyComplete();
    }

    private double hierarchicalTotalTimeMetricOf(final String method) {
        double time;
        try {
            time = meterRegistry.get("client.clientId." + method + ".raw.success").timer().totalTime(TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            time = 0D;
        }
        return time;
    }

    private long hierarchicalCountMetricOf(final String method, String state) {
        long count;
        try {
            count = meterRegistry.get("client.clientId." + method + ".raw." + state).timer().count();
        } catch (Exception e) {
            count = 0L;
        }
        return count;
    }

    private double totalDimensionalTimeMetricOf(String method) {
        double time;
        try {
            time = meterRegistry.get("http_client_requests")
                .tag("client", "clientId")
                .tag("endpoint", method)
                .tag("type", "raw")
                .tag("status", "success")
                .tag(GRAPHITE_ID, "client.clientId." + method + ".raw.success")
                .timer().totalTime(TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            time = 0D;
        }
        return time;
    }

    private long countDimensionalMetricOf(String method, String status) {
        long count;
        try {
            count = meterRegistry.get("http_client_requests")
                .tag("client", "clientId")
                .tag("endpoint", method)
                .tag("type", "raw")
                .tag("status", status)
                .tag(GRAPHITE_ID, "client.clientId." + method + ".raw." + status)
                .timer().count();
        } catch (Exception e) {
            count = 0L;
        }
        return count;
    }
}
