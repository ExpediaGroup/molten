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

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hotels.molten.core.metrics.MoltenMetrics;
import com.hotels.molten.healthcheck.HealthIndicatorWatcher;
import com.hotels.molten.test.mockito.ReactiveMock;
import com.hotels.molten.test.mockito.ReactiveMockitoAnnotations;

/**
 * Unit test for {@link CircuitBreakingReactiveProxyFactory}.
 */
public class CircuitBreakingReactiveProxyFactoryTest {
    private static final String OK = "ok";
    private static final String CLIENT_ID = "clientId";
    @ReactiveMock
    private Camoo service;
    private HealthIndicatorWatcher watcher = indicator -> { };
    private SimpleMeterRegistry meterRegistry;

    @BeforeMethod
    public void initContext() {
        ReactiveMockitoAnnotations.initMocks(this);
        meterRegistry = new SimpleMeterRegistry();
    }

    @AfterMethod
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @Test
    public void should_delegate_call_mono() {
        //Given
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        when(service.get(PARAM)).thenReturn(Mono.just(OK));
        var proxyFactory = new CircuitBreakingReactiveProxyFactory(CLIENT_ID, CircuitBreakerConfig.custom().build(), watcher);
        proxyFactory.setMeterRegistry(meterRegistry);
        Camoo wrappedService = proxyFactory.wrap(Camoo.class, service);
        //When
        StepVerifier.create(wrappedService.get(PARAM))
            .thenAwait()
            .expectNext(OK)
            .expectComplete()
            .verify();
        //Then
        assertThat(meterRegistry.get("http_client_request_circuit_successful")
            .tag("client", CLIENT_ID)
            .tag("endpoint", "get")
            .tag(GRAPHITE_ID, "client.clientId.get.circuit.successful")
            .gauge()
            .value()).isEqualTo(1);
    }

    @Test
    public void should_delegate_call_for_empty_mono() {
        when(service.completable(PARAM)).thenReturn(Mono.empty());

        Camoo wrappedService = new CircuitBreakingReactiveProxyFactory(CLIENT_ID, CircuitBreakerConfig.custom().build(), watcher).wrap(Camoo.class, service);
        StepVerifier.create(wrappedService.completable(PARAM))
            .thenAwait()
            .verifyComplete();
    }

    @Test
    public void should_open_and_close_circuit_based_on_configuration() throws InterruptedException {
        CircuitBreakerConfig circuitBreakerConfiguration = CircuitBreakerConfig.custom()
            .failureRateThreshold(25F)
            .slidingWindow(10, 10, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .permittedNumberOfCallsInHalfOpenState(2)
            .waitDurationInOpenState(Duration.ofMillis(500))
            .recordException(exception -> exception instanceof TemporaryServiceInvocationException)
            .build();
        Camoo wrappedService = new CircuitBreakingReactiveProxyFactory(CLIENT_ID, circuitBreakerConfiguration, watcher).wrap(Camoo.class, service);

        // let's assume service works fine for 13 calls. the ringbitset is saturated after 10 calls
        when(service.get(PARAM)).thenReturn(Mono.just(OK));
        StepVerifier.create(Flux.range(0, 13).flatMap(i -> wrappedService.get(PARAM))).expectNextCount(13).expectComplete().verify();
        // things go wrong from now on
        when(service.get(PARAM)).thenReturn(Mono.error(new TemporaryServiceInvocationException("doh", Camoo.class, new NullPointerException())));
        StepVerifier.create(wrappedService.get(PARAM)).expectErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(NullPointerException.class))).verify();
        StepVerifier.create(wrappedService.get(PARAM)).expectErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(NullPointerException.class))).verify();
        // CB is not just open
        StepVerifier.create(wrappedService.get(PARAM)).expectErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(NullPointerException.class))).verify();
        // at this point 30% of recent calls have failed. this should open the circuit
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(CallNotPermittedException.class)))
            .verify();

        // wait back-off duration
        Thread.sleep(Duration.ofMillis(600).toMillis());

        StepVerifier.create(wrappedService.get(PARAM)).expectErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(NullPointerException.class))).verify();
        // the service recovers
        when(service.get(PARAM)).thenReturn(Mono.just(OK));
        StepVerifier.create(wrappedService.get(PARAM)).expectNext(OK).expectComplete().verify();
        // half-open ringbitset is saturated here but we are at 50% failure rate
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(CallNotPermittedException.class)))
            .verify();
        // let's wait for next try
        Thread.sleep(Duration.ofMillis(600).toMillis());
        StepVerifier.create(wrappedService.get(PARAM)).expectNext(OK).expectComplete().verify();
        StepVerifier.create(wrappedService.get(PARAM)).expectNext(OK).expectComplete().verify();
        // we are at 100% success rate for the last 2 calls (size of halfopen ringbitset)

        when(service.get(PARAM)).thenReturn(Mono.error(new TemporaryServiceInvocationException("doh", Camoo.class, new NullPointerException())));
        //CB should be closed by now so we get the error
        StepVerifier.create(wrappedService.get(PARAM)).expectErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(NullPointerException.class))).verify();
    }
}
