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

import static com.hotels.molten.http.client.LogMatcher.matchesLogEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.SocketException;
import java.util.concurrent.TimeoutException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import retrofit2.Response;

import com.hotels.molten.test.mockito.ReactiveMock;
import com.hotels.molten.test.mockito.ReactiveMockitoAnnotations;

/**
 * Unit test for {@link CallLoggingReactiveProxyFactory}.
 */
public class CallLoggingReactiveProxyFactoryTest {
    private static final String GROUP_ID = "grpid";
    private static final String OK = "ok";
    @ReactiveMock
    private Camoo service;
    private Logger callLogger = (Logger) LoggerFactory.getLogger(CallLog.class);
    @Mock
    private Appender<ILoggingEvent> appender;
    private Camoo wrappedService;

    @BeforeMethod
    public void initContext() {
        ReactiveMockitoAnnotations.initMocks(this);
        wrappedService = new CallLoggingReactiveProxyFactory(GROUP_ID).wrap(Camoo.class, service);
        when(appender.getName()).thenReturn("MOCK");
        callLogger.setLevel(Level.ALL);
        callLogger.addAppender(appender);
    }

    @AfterMethod
    public void clearContext() {
        callLogger.detachAppender(appender);
    }

    @Test
    public void shouldLogSuccessfulCall() throws InterruptedException {
        when(service.get(200)).thenReturn(Mono.just(OK));

        StepVerifier.create(wrappedService.get(200))
            .thenAwait()
            .expectNext(OK)
            .expectComplete()
            .verify();

        verify(appender).doAppend(
            argThat(matchesLogEvent(Level.DEBUG, matchesPattern("target=com.hotels.molten.http.client.Camoo#get circuit=grpid duration=\\d+ result=SUCCESS"))));
    }

    @DataProvider(name = "errorCases")
    public Object[][] getErrorCases() {
        return new Object[][]{
            new Object[]{new NullPointerException(), "target=com.hotels.molten.http.client.Camoo#get circuit=grpid duration=\\d+ result=FAIL "
                    + "shortCircuited=false rejected=false timedOut=false retry=0 error=java.lang.NullPointerException null"},
            new Object[]{new TemporaryServiceInvocationException("doh", Camoo.class,
                BulkheadFullException.createBulkheadFullException(Bulkhead.of("full", BulkheadConfig.ofDefaults()))),
                "target=com.hotels.molten.http.client.Camoo#get circuit=grpid duration=\\d+ result=FAIL shortCircuited=false rejected=true timedOut=false retry=0 "
                    + "error=io.github.resilience4j.bulkhead.BulkheadFullException Bulkhead 'full' is full and does not permit further calls"},
            new Object[]{new TemporaryServiceInvocationException("doh", Camoo.class, new TimeoutException("timedout")), "target=com.hotels.molten.http.client.Camoo#get "
                    + "circuit=grpid duration=\\d+ result=FAIL shortCircuited=false rejected=false timedOut=true retry=0 error=java.util.concurrent.TimeoutException"
                    + " timedout"},
            new Object[]{new TemporaryServiceInvocationException("doh", Camoo.class, CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("cb"))),
                "target=com.hotels.molten.http.client.Camoo#get circuit=grpid duration=\\d+ result=FAIL shortCircuited=true rejected=false timedOut=false retry=0 "
                    + "error=io.github.resilience4j.circuitbreaker.CallNotPermittedException CircuitBreaker 'cb' is CLOSED and does not permit further calls"},
            new Object[]{new PermanentServiceInvocationException("doh", Camoo.class, new SocketException("connection reset")), "target=com.hotels.molten.http.client.Camoo#get "
                    + "circuit=grpid duration=\\d+ result=FAIL shortCircuited=false rejected=false timedOut=false retry=0 error=java.net.SocketException"
                    + " connection reset"},
            new Object[]{new PermanentServiceInvocationException("doh", Camoo.class,
                    new HttpException(new retrofit2.HttpException(Response.error(400, ResponseBody.create("this is bad", MediaType.parse("text/plain")))))),
                         "target=com.hotels.molten.http.client.Camoo#get circuit=grpid duration=\\d+ result=FAIL shortCircuited=false rejected=false timedOut=false retry=0 "
                    + "error=com.hotels.molten.http.client.HttpException httpStatus=400 error_message=Response.error\\(\\) error_body=this is bad"}
        };
    }

    @Test(dataProvider = "errorCases")
    public void shouldLogFailedCallWithCause(Exception error, String logPattern) throws InterruptedException {
        when(service.get(200)).thenReturn(Mono.error(error));

        StepVerifier.create(wrappedService.get(200))
            .thenAwait()
            .expectErrorSatisfies(ex -> assertThat(ex).isSameAs(error))
            .verify();

        verify(appender).doAppend(argThat(matchesLogEvent(Level.WARN, matchesPattern(logPattern))));
    }
}
