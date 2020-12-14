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

import static com.hotels.molten.http.client.Camoo.PARAM;
import static com.hotels.molten.http.client.Camoo.RESULT;
import static com.hotels.molten.http.client.HttpServiceInvocationExceptionHandlingReactiveProxyFactory.DEFAULT_FAILED_RESPONSE_PREDICATE;
import static com.hotels.molten.http.client.ReactiveTestSupport.anErrorWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import retrofit2.Response;

/**
 * Unit test for {@link HttpServiceInvocationExceptionHandlingReactiveProxyFactory}.
 */
public class HttpServiceInvocationExceptionHandlingReactiveProxyFactoryTest {
    private static final ResponseBody RESPONSE_BODY = ResponseBody.create("", MediaType.parse("application/json"));
    private static final String EXCEPTION_MESSAGE_PREFIX = "service=com.hotels.molten.http.client.Camoo";
    @Mock
    private Camoo service;
    private HttpServiceInvocationExceptionHandlingReactiveProxyFactory proxyFactory;

    @BeforeMethod
    public void initContext() {
        MockitoAnnotations.initMocks(this);
        proxyFactory = new HttpServiceInvocationExceptionHandlingReactiveProxyFactory(DEFAULT_FAILED_RESPONSE_PREDICATE);
    }

    @Test
    public void shouldDelegateCallToActualService() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.just(Camoo.RESULT));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM)).expectNext(RESULT).expectComplete().verify();
    }

    @Test
    public void shouldCatchServiceInvocationExceptions() {
        NullPointerException npe = new NullPointerException();
        when(service.get(Camoo.PARAM)).thenThrow(npe);

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(anErrorWith(PermanentServiceInvocationException.class, sameInstance(npe)))
            .verify();
    }

    @DataProvider(name = "permanentExceptions")
    public Object[][] getPermanentExceptions() {
        return new Object[][] {
            new Object[] {new NullPointerException()}
        };
    }

    @Test(dataProvider = "permanentExceptions")
    public void shouldMapToPermanentServiceInvocationException(Exception cause) {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(cause));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(anErrorWith(PermanentServiceInvocationException.class, sameInstance(cause)))
            .verify();
    }

    @DataProvider(name = "temporaryExceptions")
    public Object[][] getTemporaryExceptions() {
        return new Object[][] {
            new Object[] {new SocketTimeoutException()},
            new Object[] {new ConnectException()},
            new Object[] {new SocketException()},
            new Object[] {new IOException()}
        };
    }

    @Test(dataProvider = "temporaryExceptions")
    public void shouldMapToTemporaryServiceInvocationException(Exception cause) {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(cause));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, sameInstance(cause)))
            .verify();
    }

    @Test
    public void shouldMapCertainHttpExceptionsToPermanentServiceInvocationExceptionWrapped() {
        retrofit2.HttpException exception = new retrofit2.HttpException(Response.error(404, RESPONSE_BODY));
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(exception));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(ex -> assertThat(ex)
                .isInstanceOf(PermanentServiceInvocationException.class)
                .hasCauseInstanceOf(HttpException.class)
                .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("httpStatus=404")))
            .verify();
    }

    @Test
    public void shouldMapCertainHttpExceptionsToTemporaryServiceInvocationExceptionWrapped() {
        retrofit2.HttpException exception = new retrofit2.HttpException(Response.error(503, RESPONSE_BODY));
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(exception));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(ex -> assertThat(ex)
                .isInstanceOf(TemporaryServiceInvocationException.class)
                .hasCauseInstanceOf(HttpException.class)
                .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("httpStatus=503")))
            .verify();
    }

    @Test
    public void shouldLogServiceNameAndStatusCodeWhenAHttpExceptionCausedTemporaryExceptionHappens() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(new retrofit2.HttpException(Response.error(503, RESPONSE_BODY))));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(ex -> assertThat(ex)
                .hasMessageContaining(EXCEPTION_MESSAGE_PREFIX)
                .hasMessageContaining("Temporary service invocation exception")
                .hasCauseInstanceOf(HttpException.class)
                .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("httpStatus=503")))
            .verify();
    }

    @Test
    public void shouldLogServiceNameWhenAConnectionExceptionHappens() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(new ConnectException()));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(ex -> assertThat(ex)
                .hasMessageContaining(EXCEPTION_MESSAGE_PREFIX)
                .hasMessageContaining("Couldn't connect to service")
                .hasCauseInstanceOf(ConnectException.class))
            .verify();
    }

    @Test
    public void shouldLogServiceNameWhenASocketTimeoutExceptionHappens() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(new SocketTimeoutException()));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorMessage(EXCEPTION_MESSAGE_PREFIX + " Service invocation timed out")
            .verify();
    }

    @Test
    public void shouldLogServiceNameWhenAnUnExceptedExceptionHappens() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(new NullPointerException()));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorMessage(EXCEPTION_MESSAGE_PREFIX + " Unexpected exception during service invocation.")
            .verify();
    }

    @Test
    public void shouldLogServiceNameAndStatusCodeWhenAHttpExceptionCausedPermanentExceptionHappens() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(new retrofit2.HttpException(Response.error(404, RESPONSE_BODY))));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .verifyError(PermanentServiceInvocationException.class);
    }

    @Test
    public void shouldHandleHttpExceptionForRetrofitResponse() {
        when(service.getResponse()).thenReturn(Mono.just(Response.error(404, RESPONSE_BODY)));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.getResponse())
            .expectErrorSatisfies(ex -> assertThat(ex)
                .isInstanceOf(PermanentServiceInvocationException.class)
                .hasCauseInstanceOf(HttpException.class)
                .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("httpStatus=404")))
            .verify();
    }

    @Test
    public void shouldApplyCustomResponseSuccessfulPredicate() {
        proxyFactory = new HttpServiceInvocationExceptionHandlingReactiveProxyFactory(response -> !response.isSuccessful() || response.code() == 204);
        when(service.getResponse()).thenReturn(Mono.just(Response.success(204, null)));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.getResponse())
            .expectErrorSatisfies(ex -> assertThat(ex)
                .isInstanceOf(TemporaryServiceInvocationException.class)
                .hasCauseInstanceOf(HttpException.class)
                .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("httpStatus=204")))
            .verify();
    }
}
