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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import retrofit2.Response;

/**
 * Unit test for {@link HttpServiceInvocationExceptionHandlingReactiveProxyFactory}.
 */
@ExtendWith(MockitoExtension.class)
public class HttpServiceInvocationExceptionHandlingReactiveProxyFactoryTest {
    private static final String RAW_ERROR_RESPONSE_BODY = "error-body";
    private static final String EXCEPTION_MESSAGE_PREFIX = "service=com.hotels.molten.http.client.Camoo";
    @Mock
    private Camoo service;
    private HttpServiceInvocationExceptionHandlingReactiveProxyFactory proxyFactory;

    @BeforeEach
    void initContext() {
        proxyFactory = new HttpServiceInvocationExceptionHandlingReactiveProxyFactory(DEFAULT_FAILED_RESPONSE_PREDICATE);
    }

    @Test
    void should_delegate_call_to_actual_service() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.just(Camoo.RESULT));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM)).expectNext(RESULT).expectComplete().verify();
    }

    @Test
    void should_catch_service_invocation_exceptions() {
        NullPointerException npe = new NullPointerException();
        when(service.get(Camoo.PARAM)).thenThrow(npe);

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(anErrorWith(PermanentServiceInvocationException.class, sameInstance(npe)))
            .verify();
    }

    static Object[][] permanentExceptions() {
        return new Object[][] {
            new Object[] {new NullPointerException()}
        };
    }

    @ParameterizedTest
    @MethodSource("permanentExceptions")
    void should_map_to_permanent_service_invocation_exception(Exception cause) {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(cause));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(anErrorWith(PermanentServiceInvocationException.class, sameInstance(cause)))
            .verify();
    }

    static Object[][] temporaryExceptions() {
        return new Object[][] {
            new Object[] {new SocketTimeoutException()},
            new Object[] {new ConnectException()},
            new Object[] {new SocketException()},
            new Object[] {new IOException()}
        };
    }

    @ParameterizedTest
    @MethodSource("temporaryExceptions")
    void should_map_to_temporary_service_invocation_exception(Exception cause) {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(cause));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, sameInstance(cause)))
            .verify();
    }

    @Test
    void should_map_certain_http_exceptions_to_permanent_service_invocation_exception_wrapped() {
        var exception = new retrofit2.HttpException(Response.error(404, ResponseBody.create(RAW_ERROR_RESPONSE_BODY, MediaType.parse("application/json"))));
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(exception));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(ex -> assertThat(ex)
                .isInstanceOf(PermanentServiceInvocationException.class)
                .hasCauseInstanceOf(HttpException.class)
                .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("http_status=404")))
            .verify();
    }

    @Test
    void should_map_certain_http_exceptions_to_temporary_service_invocation_exception_wrapped() {
        var exception = new retrofit2.HttpException(Response.error(503, ResponseBody.create(RAW_ERROR_RESPONSE_BODY, MediaType.parse("application/json"))));
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(exception));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(ex -> assertThat(ex)
                .isInstanceOf(TemporaryServiceInvocationException.class)
                .hasCauseInstanceOf(HttpException.class)
                .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("http_status=503")))
            .verify();
    }

    @Test
    void should_log_service_name_and_status_code_when_ahttp_exception_caused_temporary_exception_happens() {
        when(service.get(Camoo.PARAM))
            .thenReturn(Mono.error(new retrofit2.HttpException(Response.error(503, ResponseBody.create(RAW_ERROR_RESPONSE_BODY, MediaType.parse("application/json"))))));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorSatisfies(ex -> assertThat(ex)
                .hasMessageContaining(EXCEPTION_MESSAGE_PREFIX)
                .hasMessageContaining("Temporary service invocation exception")
                .hasCauseInstanceOf(HttpException.class)
                .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("http_status=503")))
            .verify();
    }

    @Test
    void should_log_service_name_when_aconnection_exception_happens() {
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
    void should_log_service_name_when_asocket_timeout_exception_happens() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(new SocketTimeoutException()));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorMessage(EXCEPTION_MESSAGE_PREFIX + " Service invocation timed out")
            .verify();
    }

    @Test
    void should_log_service_name_when_an_un_excepted_exception_happens() {
        when(service.get(Camoo.PARAM)).thenReturn(Mono.error(new NullPointerException()));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .expectErrorMessage(EXCEPTION_MESSAGE_PREFIX + " Unexpected exception during service invocation.")
            .verify();
    }

    @Test
    void should_log_service_name_and_status_code_when_ahttp_exception_caused_permanent_exception_happens() {
        when(service.get(Camoo.PARAM))
            .thenReturn(Mono.error(new retrofit2.HttpException(Response.error(404, ResponseBody.create(RAW_ERROR_RESPONSE_BODY, MediaType.parse("application/json"))))));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.get(PARAM))
            .verifyError(PermanentServiceInvocationException.class);
    }

    @Test
    void should_be_possible_to_read_error_body_twice() {
        Response<String> errorResponse = Response.error(404, ResponseBody.create(RAW_ERROR_RESPONSE_BODY, MediaType.parse("application/json")));
        var httpException = new HttpException(new retrofit2.HttpException(errorResponse));
        assertThat(httpException.getMessage()).contains("error_body=" + RAW_ERROR_RESPONSE_BODY);
        assertThat(new String(httpException.getErrorBody(), httpException.getCharset())).isEqualTo(RAW_ERROR_RESPONSE_BODY);
        assertThat(httpException.getErrorBody()).isEqualTo(httpException.getErrorBody());
    }

    @Test
    void should_handle_http_exception_for_retrofit_response() {
        Response<String> expectedResponse = Response.error(404, ResponseBody.create(RAW_ERROR_RESPONSE_BODY, MediaType.parse("application/json")));
        when(service.getResponse()).thenReturn(Mono.just(expectedResponse));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.getResponse())
            .expectErrorSatisfies(ex -> assertThat(ex)
                .isInstanceOf(PermanentServiceInvocationException.class)
                .hasCauseInstanceOf(HttpException.class)
                .satisfies(e -> {
                    assertThat(e.getCause()).hasMessageContaining("http_status=" + expectedResponse.code());
                    assertThat(e.getCause()).hasMessageContaining("error_message=" + expectedResponse.message());
                    assertThat(e.getCause()).hasMessageContaining("error_body=" + RAW_ERROR_RESPONSE_BODY);
                }))
            .verify();
    }

    @Test
    void should_apply_custom_response_successful_predicate() {
        proxyFactory = new HttpServiceInvocationExceptionHandlingReactiveProxyFactory(response -> !response.isSuccessful() || response.code() == 204);
        when(service.getResponse()).thenReturn(Mono.just(Response.success(204, null)));

        Camoo wrappedService = proxyFactory.wrap(Camoo.SERVICE_TYPE, service);
        StepVerifier.create(wrappedService.getResponse())
            .expectErrorSatisfies(ex -> assertThat(ex)
                .isInstanceOf(TemporaryServiceInvocationException.class)
                .hasCauseInstanceOf(HttpException.class)
                .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("http_status=204")))
            .verify();
    }
}
