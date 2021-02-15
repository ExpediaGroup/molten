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
import static com.hotels.molten.http.client.LogMatcher.matchesLogEvent;
import static com.hotels.molten.http.client.ReactiveTestSupport.anErrorWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Unit test for {@link RetryingReactiveProxyFactory}.
 */
@Slf4j
@Listeners(MockitoTestNGListener.class)
public class RetryingReactiveProxyFactoryTest {
    private static final String CLIENT_ID = "clientId";
    private RetryingReactiveProxyFactory proxyFactory;
    @Mock
    private Camoo service;
    private MeterRegistry meterRegistry;
    private Logger callLogger = (Logger) LoggerFactory.getLogger(CallLog.class);
    @Mock
    private Appender<ILoggingEvent> appender;

    @BeforeMethod
    public void initContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        meterRegistry = new SimpleMeterRegistry();
        proxyFactory = new RetryingReactiveProxyFactory(2, CLIENT_ID).withMetrics(meterRegistry);
        lenient().when(appender.getName()).thenReturn("MOCK");
        callLogger.addAppender(appender);
    }

    @AfterMethod
    public void clearContext() {
        callLogger.detachAppender(appender);
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @Test
    public void should_delegate_call_to_actual_service() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.just(Camoo.RESULT));

        StepVerifier.create(proxyFactory.wrap(Camoo.SERVICE_TYPE, service).get(Camoo.PARAM)).expectNext(Camoo.RESULT).verifyComplete();
    }

    @Test
    public void should_retry_temporary_failure() {
        when(service.get(Camoo.PARAM))
            .thenReturn(Mono.error(new TemporaryServiceInvocationException("doh", service.getClass(), new NullPointerException())))
            .thenReturn(Mono.just(Camoo.RESULT));

        StepVerifier.create(proxyFactory.wrap(Camoo.SERVICE_TYPE, service).get(Camoo.PARAM)).expectNext(Camoo.RESULT).verifyComplete();
        verify(service, times(2)).get(Camoo.PARAM);
        verifyNoMoreInteractions(service);
        assertThat(hierarchicalRetryCountMetricFor(1)).isEqualTo(1);
        assertThat(hierarchicalRetryCountMetricFor(2)).isEqualTo(0);
        verify(appender).doAppend(argThat(matchesLogEvent(Level.INFO, matchesPattern(
            "target=com.hotels.molten.http.client.Camoo#get circuit=clientId duration=\\d+ result=RETRY shortCircuited=false rejected=false timedOut=false retry=1 "
                + "error=java.lang.NullPointerException null"))));
        verifyNoMoreInteractions(appender);
    }

    @Test
    public void should_retry_up_to_max_retries() {
        when(service.get(Camoo.PARAM))
            .thenReturn(Mono.error(new TemporaryServiceInvocationException("doh", service.getClass(), new NullPointerException())))
            .thenReturn(Mono.error(new TemporaryServiceInvocationException("doh", service.getClass(), new IllegalArgumentException("nope"))))
            .thenReturn(Mono.just(Camoo.RESULT));

        StepVerifier.create(proxyFactory.wrap(Camoo.SERVICE_TYPE, service).get(Camoo.PARAM)).expectNext(Camoo.RESULT).verifyComplete();
        verify(service, times(3)).get(Camoo.PARAM);
        verifyNoMoreInteractions(service);
        assertThat(hierarchicalRetryCountMetricFor(1)).isEqualTo(1);
        assertThat(hierarchicalRetryCountMetricFor(2)).isEqualTo(1);
        verify(appender).doAppend(argThat(matchesLogEvent(Level.INFO, matchesPattern(
            "target=com.hotels.molten.http.client.Camoo#get circuit=clientId duration=\\d+ result=RETRY shortCircuited=false rejected=false timedOut=false retry=1 "
                + "error=java.lang.NullPointerException null"))));
        verify(appender).doAppend(argThat(matchesLogEvent(Level.INFO, matchesPattern(
            "target=com.hotels.molten.http.client.Camoo#get circuit=clientId duration=\\d+ result=RETRY shortCircuited=false rejected=false timedOut=false retry=2 "
                + "error=java.lang.IllegalArgumentException nope"))));
        verifyNoMoreInteractions(appender);
    }

    @Test
    public void should_propagate_error_after_max_retries() {
        NullPointerException npe = new NullPointerException();
        when(service.get(Camoo.PARAM))
            .thenReturn(Mono.error(new TemporaryServiceInvocationException("doh", service.getClass(), npe)))
            .thenReturn(Mono.error(new TemporaryServiceInvocationException("doh", service.getClass(), npe)))
            .thenReturn(Mono.error(new TemporaryServiceInvocationException("doh", service.getClass(), npe)))
            .thenReturn(Mono.just(Camoo.RESULT));

        StepVerifier.create(proxyFactory.wrap(Camoo.SERVICE_TYPE, service).get(Camoo.PARAM))
            .verifyErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, sameInstance(npe)));
        verify(service, times(3)).get(Camoo.PARAM);
        verifyNoMoreInteractions(service);
        assertThat(hierarchicalRetryCountMetricFor(1)).isEqualTo(1);
        assertThat(hierarchicalRetryCountMetricFor(2)).isEqualTo(1);
        verify(appender).doAppend(argThat(matchesLogEvent(Level.INFO, matchesPattern(
            "target=com.hotels.molten.http.client.Camoo#get circuit=clientId duration=\\d+ result=RETRY shortCircuited=false rejected=false timedOut=false retry=1 "
                + "error=java.lang.NullPointerException null"))));
        verify(appender).doAppend(argThat(matchesLogEvent(Level.INFO, matchesPattern(
            "target=com.hotels.molten.http.client.Camoo#get circuit=clientId duration=\\d+ result=RETRY shortCircuited=false rejected=false timedOut=false retry=2 "
                + "error=java.lang.NullPointerException null"))));
        verify(appender).doAppend(argThat(matchesLogEvent(Level.WARN, matchesPattern(
            "target=com.hotels.molten.http.client.Camoo#get circuit=clientId duration=\\d+ result=FAIL shortCircuited=false rejected=false timedOut=false retry=2 "
                + "error=java.lang.NullPointerException null"))));
        verifyNoMoreInteractions(appender);
    }

    @Test
    public void should_not_retry_permanent_error() {
        NullPointerException firstException = new NullPointerException();
        when(service.get(Camoo.PARAM))
            .thenReturn(Mono.error(new PermanentServiceInvocationException("doh", service.getClass(), firstException)))
            .thenReturn(Mono.error(new PermanentServiceInvocationException("doh", service.getClass(), new NullPointerException())))
            .thenReturn(Mono.just(Camoo.RESULT));

        StepVerifier.create(proxyFactory.wrap(Camoo.SERVICE_TYPE, service).get(Camoo.PARAM))
            .verifyErrorSatisfies(anErrorWith(PermanentServiceInvocationException.class, sameInstance(firstException)));
        verify(service, times(1)).get(Camoo.PARAM);
        verifyNoMoreInteractions(service);
        assertThat(hierarchicalRetryCountMetricFor(1)).isEqualTo(0);
        assertThat(hierarchicalRetryCountMetricFor(2)).isEqualTo(0);
        verify(appender).doAppend(argThat(matchesLogEvent(Level.WARN, matchesPattern(
            "target=com.hotels.molten.http.client.Camoo#get circuit=clientId duration=\\d+ result=FAIL shortCircuited=false rejected=false timedOut=false retry=0 "
                + "error=java.lang.NullPointerException null"))));
        verifyNoMoreInteractions(appender);
    }

    @Test
    public void should_emit_service_invocation_error() {
        NullPointerException npe = new NullPointerException();
        when(service.get(Camoo.PARAM)).thenThrow(npe);

        StepVerifier.create(proxyFactory.wrap(Camoo.SERVICE_TYPE, service).get(Camoo.PARAM))
            .verifyErrorSatisfies(anErrorWith(PermanentServiceInvocationException.class, sameInstance(npe)));
        verify(service, times(1)).get(Camoo.PARAM);
        verifyNoMoreInteractions(service);
        assertThat(hierarchicalRetryCountMetricFor(1)).isEqualTo(0);
        assertThat(hierarchicalRetryCountMetricFor(2)).isEqualTo(0);
    }

    @Test
    public void should_report_dimensional_metrics_when_enabled() {
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        when(service.get(Camoo.PARAM))
            .thenReturn(Mono.error(new TemporaryServiceInvocationException("doh", service.getClass(), new NullPointerException())))
            .thenReturn(Mono.just(Camoo.RESULT));

        StepVerifier.create(proxyFactory.wrap(Camoo.SERVICE_TYPE, service).get(Camoo.PARAM)).expectNext(Camoo.RESULT).verifyComplete();
        verify(service, times(2)).get(Camoo.PARAM);
        verifyNoMoreInteractions(service);
        assertThat(dimensionalRetryCountMetricFor(1)).isEqualTo(1);
        assertThat(dimensionalRetryCountMetricFor(2)).isEqualTo(0);
    }

    private double hierarchicalRetryCountMetricFor(int retryIdx) {
        double count;
        try {
            count = meterRegistry.get("client.clientId.get.retries.retry" + retryIdx).counter().count();
        } catch (Exception e) {
            LOG.info("counter not found for idx {}", retryIdx);
            count = 0D;
        }
        return count;
    }

    private double dimensionalRetryCountMetricFor(int retryIdx) {
        double count;
        try {
            count = meterRegistry.get("http_client_request_retries")
                .tag("client", "clientId")
                .tag("endpoint", "get")
                .tag("retry", "retry" + retryIdx)
                .tag(GRAPHITE_ID, "client.clientId.get.retries.retry" + retryIdx)
                .counter().count();
        } catch (Exception e) {
            count = 0L;
        }
        return count;
    }
}
