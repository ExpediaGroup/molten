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
import static com.hotels.molten.http.client.ReactiveTestSupport.anErrorWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import com.hotels.molten.core.metrics.MoltenMetrics;
import com.hotels.molten.test.AssertSubscriber;

/**
 * Unit test for {@link BulkheadingReactiveProxyFactory}.
 */
@Listeners(MockitoTestNGListener.class)
public class BulkheadingReactiveProxyFactoryTest {
    private static final AtomicInteger ISO_GRP_IDX = new AtomicInteger();
    private static final int REQUEST_PARAM = 1;
    private static final String RESULT = "result";
    private static final Class<Camoo> API = Camoo.class;
    private static final String CLIENT_ID = "clientId";
    @Mock
    private Camoo service;
    private VirtualTimeScheduler scheduler;
    private SimpleMeterRegistry meterRegistry;

    @BeforeMethod
    public void initContext() {
        scheduler = VirtualTimeScheduler.create();
        VirtualTimeScheduler.set(scheduler);
        meterRegistry = new SimpleMeterRegistry();
    }

    @AfterMethod
    public void clearContext() {
        VirtualTimeScheduler.reset();
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @Test
    public void should_prevent_too_many_concurrent_requests() {
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        AtomicInteger callCount = new AtomicInteger();
        when(service.get(REQUEST_PARAM)).thenReturn(Mono.just(RESULT).doOnSuccess(c -> callCount.incrementAndGet()).delayElement(Duration.ofMillis(50)));

        BulkheadingReactiveProxyFactory factory = new BulkheadingReactiveProxyFactory(bulkHeadConfig().maxConcurrency(1).build());
        factory.setMeterRegistry(meterRegistry, CLIENT_ID);
        Camoo wrappedService = factory.wrap(API, service);
        AssertSubscriber<String> first = new AssertSubscriber<>();
        wrappedService.get(REQUEST_PARAM).subscribe(first);
        scheduler.advanceTimeBy(Duration.ofMillis(20));
        StepVerifier.create(wrappedService.get(REQUEST_PARAM))
            .verifyErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(BulkheadFullException.class)));
        assertThat(meterRegistry.get("http_client_request_bulkhead_available")
            .tag("client", CLIENT_ID)
            .tag("endpoint", "get")
            .tag(GRAPHITE_ID, "client." + CLIENT_ID + ".get.bulkhead.available")
            .gauge()
            .value()).isEqualTo(0);
        scheduler.advanceTimeBy(Duration.ofMillis(30));
        first.await();
        StepVerifier.create(wrappedService.get(REQUEST_PARAM))
            .then(() -> scheduler.advanceTimeBy(Duration.ofMillis(50)))
            .expectNext(RESULT)
            .verifyComplete();
        verify(service, times(2)).get(REQUEST_PARAM);
        assertThat(callCount.get()).isEqualTo(2);
        assertThat(meterRegistry.get("http_client_request_bulkhead_available")
            .tag("client", CLIENT_ID)
            .tag("endpoint", "get")
            .tag(GRAPHITE_ID, "client." + CLIENT_ID + ".get.bulkhead.available")
            .gauge()
            .value()).isEqualTo(1);
        assertThat(meterRegistry.get("http_client_request_bulkhead_rejected")
            .tag("client", CLIENT_ID)
            .tag("endpoint", "get")
            .tag(GRAPHITE_ID, "client." + CLIENT_ID + ".get.bulkhead.rejected")
            .gauge()
            .value()).isEqualTo(1);
    }

    private BulkheadConfiguration.Builder bulkHeadConfig() {
        return BulkheadConfiguration.builder().isolationGroupName("iso" + ISO_GRP_IDX.incrementAndGet());
    }
}
