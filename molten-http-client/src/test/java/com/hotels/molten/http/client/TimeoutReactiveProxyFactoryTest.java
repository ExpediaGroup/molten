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
import static com.hotels.molten.http.client.Camoo.PARAM;
import static com.hotels.molten.http.client.ReactiveTestSupport.anErrorWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.isA;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import io.micrometer.core.instrument.MeterRegistry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hotels.molten.core.metrics.MoltenMetrics;
import com.hotels.molten.test.mockito.ReactiveMock;
import com.hotels.molten.test.mockito.ReactiveMockitoAnnotations;

/**
 * Unit test for {@link TimeoutReactiveProxyFactory}.
 */
public class TimeoutReactiveProxyFactoryTest {
    private static final String OK = "ok";
    private static final Duration TIMEOUT = Duration.ofMillis(1000);
    private static final String CLIENT_ID = "clientId";
    @ReactiveMock
    private Camoo service;
    private TimeoutReactiveProxyFactory proxyFactory;
    private Camoo wrappedService;
    private MeterRegistry meterRegistry;

    @BeforeMethod
    public void initContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        ReactiveMockitoAnnotations.initMocks(this);
        proxyFactory = new TimeoutReactiveProxyFactory(TIMEOUT);
        meterRegistry = MeterRegistrySupport.simpleRegistry();
        proxyFactory.withMetrics(meterRegistry, CLIENT_ID, "raw");
        wrappedService = proxyFactory.wrap(Camoo.class, service);
    }

    @AfterMethod
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @Test
    public void should_delegate_call_for_mono() {
        when(service.get(PARAM)).thenReturn(Mono.just(OK));

        StepVerifier.withVirtualTime(() -> wrappedService.get(PARAM))
            .thenAwait()
            .expectNext(OK)
            .verifyComplete();

        assertThat(hierarchicalCountMetricOf("get", "timeout.raw")).isEqualTo(0D);
    }

    @Test
    public void should_delegate_call_for_completable() {
        when(service.completable(PARAM)).thenReturn(Mono.empty());

        StepVerifier.withVirtualTime(() -> wrappedService.completable(PARAM))
            .thenAwait()
            .verifyComplete();

        assertThat(hierarchicalCountMetricOf("get", "timeout.raw")).isEqualTo(0D);
    }

    @Test
    public void should_timeout_call() {
        StepVerifier.withVirtualTime(() -> {
            when(service.get(PARAM)).thenReturn(Mono.just(OK).delayElement(TIMEOUT));
            return wrappedService.get(PARAM);
        })
            .thenAwait(TIMEOUT)
            .expectErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(TimeoutException.class)))
            .verify();
        assertThat(hierarchicalCountMetricOf("get", "timeout.raw")).isEqualTo(1D);
    }

    @Test
    public void should_use_dimensional_metrics_when_enabled() {
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        StepVerifier.withVirtualTime(() -> {
            when(service.get(PARAM)).thenReturn(Mono.just(OK).delayElement(TIMEOUT));
            return wrappedService.get(PARAM);
        })
            .thenAwait(TIMEOUT)
            .expectErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(TimeoutException.class)))
            .verify();
        double count = meterRegistry.get("http_client_request_timeouts")
            .tag("client", CLIENT_ID)
            .tag("endpoint", "get")
            .tag("type", "raw")
            .tag(GRAPHITE_ID, "client.clientId.get.timeout.raw")
            .counter().count();
        assertThat(count).isEqualTo(1D);
    }

    @Test
    public void should_propagate_service_invocation_exception_as_is() {
        PermanentServiceInvocationException expectedException = new PermanentServiceInvocationException("doh", Camoo.class, new NullPointerException());
        when(service.get(PARAM)).thenReturn(Mono.error(expectedException));

        StepVerifier.withVirtualTime(() -> wrappedService.get(PARAM))
            .thenAwait()
            .expectErrorSatisfies(e -> assertThat(e).isSameAs(expectedException))
            .verify();

        assertThat(hierarchicalCountMetricOf("get", "timeout.raw")).isEqualTo(0D);
    }

    private double hierarchicalCountMetricOf(String method, String postfix) {
        double count;
        try {
            count = meterRegistry.get("client.clientId." + method + "." + postfix).counter().count();
        } catch (Exception e) {
            count = 0L;
        }
        return count;
    }
}
