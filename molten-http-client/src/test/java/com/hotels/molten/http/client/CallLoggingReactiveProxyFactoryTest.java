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
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.SocketException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import retrofit2.Response;

/**
 * Unit test for {@link CallLoggingReactiveProxyFactory}.
 */
@ExtendWith(MockitoExtension.class)
public class CallLoggingReactiveProxyFactoryTest {
    private static final String GROUP_ID = "grpid";
    private static final String OK = "ok";
    private final Logger callLogger = (Logger) LoggerFactory.getLogger(CallLog.class);
    @Mock
    private Camoo service;
    @Mock(lenient = true)
    private Appender<ILoggingEvent> appender;
    private Camoo wrappedService;

    @BeforeEach
    public void initContext() {
        wrappedService = new CallLoggingReactiveProxyFactory(GROUP_ID).wrap(Camoo.class, service);
        when(appender.getName()).thenReturn("MOCK");
        callLogger.setLevel(Level.ALL);
        callLogger.addAppender(appender);
    }

    @AfterEach
    public void clearContext() {
        callLogger.detachAppender(appender);
    }

    @Test
    public void should_log_successful_call() {
        when(service.get(200)).thenReturn(Mono.just(OK));

        StepVerifier.create(wrappedService.get(200))
            .thenAwait()
            .expectNext(OK)
            .expectComplete()
            .verify();

        verify(appender).doAppend(
            argThat(matchesLogEvent(Level.DEBUG, matchesPattern("target=com.hotels.molten.http.client.Camoo#get circuit=grpid duration=\\d+ result=SUCCESS"))));
    }

    public static Stream<Arguments> errorCases() {
        return Stream.of(
            arguments(new NullPointerException(), "target=com.hotels.molten.http.client.Camoo#get circuit=grpid duration=\\d+ result=FAIL "
                + "shortCircuited=false rejected=false timedOut=false retry=0 error=java.lang.NullPointerException null"),
            arguments(new TemporaryServiceInvocationException("doh", Camoo.class,
                    BulkheadFullException.createBulkheadFullException(Bulkhead.of("full", BulkheadConfig.ofDefaults()))),
                "target=com.hotels.molten.http.client.Camoo#get circuit=grpid duration=\\d+ result=FAIL shortCircuited=false rejected=true timedOut=false retry=0 "
                    + "error=io.github.resilience4j.bulkhead.BulkheadFullException Bulkhead 'full' is full and does not permit further calls"),
            arguments(new TemporaryServiceInvocationException("doh", Camoo.class, new TimeoutException("timedout")), "target=com.hotels.molten.http.client.Camoo#get "
                + "circuit=grpid duration=\\d+ result=FAIL shortCircuited=false rejected=false timedOut=true retry=0 error=java.util.concurrent.TimeoutException"
                + " timedout"),
            arguments(new TemporaryServiceInvocationException("doh", Camoo.class, CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("cb"))),
                "target=com.hotels.molten.http.client.Camoo#get circuit=grpid duration=\\d+ result=FAIL shortCircuited=true rejected=false timedOut=false retry=0 "
                    + "error=io.github.resilience4j.circuitbreaker.CallNotPermittedException CircuitBreaker 'cb' is CLOSED and does not permit further calls"),
            arguments(new PermanentServiceInvocationException("doh", Camoo.class, new SocketException("connection reset")), "target=com.hotels.molten.http.client.Camoo#get "
                + "circuit=grpid duration=\\d+ result=FAIL shortCircuited=false rejected=false timedOut=false retry=0 error=java.net.SocketException"
                + " connection reset"),
            arguments(new PermanentServiceInvocationException("doh", Camoo.class,
                    new HttpException(new retrofit2.HttpException(Response.error(400, ResponseBody.create("this is bad", MediaType.parse("text/plain")))))),
                "target=com.hotels.molten.http.client.Camoo#get circuit=grpid duration=\\d+ result=FAIL shortCircuited=false rejected=false timedOut=false retry=0 "
                    + "error=com.hotels.molten.http.client.HttpException http_status=400 error_message=Response.error\\(\\) error_body=this is bad")
        );
    }

    @ParameterizedTest
    @MethodSource("errorCases")
    public void shouldLogFailedCallWithCause(Exception error, String logPattern) {
        when(service.get(200)).thenReturn(Mono.error(error));

        StepVerifier.create(wrappedService.get(200))
            .thenAwait()
            .expectErrorSatisfies(ex -> assertThat(ex).isSameAs(error))
            .verify();

        verify(appender).doAppend(argThat(matchesLogEvent(Level.WARN, matchesPattern(logPattern))));
    }
}
