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

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import retrofit2.HttpException;
import retrofit2.Response;

/**
 * <p>Creates a proxy over arbitrary reactive interface and propagate emitted exceptions by translating them to either {@link TemporaryServiceInvocationException} or
 * {@link PermanentServiceInvocationException}.</p>
 * <p>{@link HttpException} are translated based on their status code. Usually client side errors are mapped to {@link PermanentServiceInvocationException} while server side
 * errors are mapped to {@link TemporaryServiceInvocationException}.</p>
 * <p>Other exceptions are converted to {@link PermanentServiceInvocationException}.</p>
 * <br>
 * <p>Any exceptions which occur when invoking the wrapped service will be translated to {@link PermanentServiceInvocationException} as well.</p>
 * <p>As retrofit doesn't translate http status codes to {@link HttpException} by default in case the response of the {@code API} is wrapped by a {@link Response} object,
 * it does using the given {@link #failedResponsePredicate predicate} to decide what http status code is considered as error.</p>
 */
@RequiredArgsConstructor
final class HttpServiceInvocationExceptionHandlingReactiveProxyFactory extends AbstractReactiveProxyFactory {
    public static final Predicate<Response<?>> DEFAULT_FAILED_RESPONSE_PREDICATE = response -> !response.isSuccessful();

    @NonNull
    private final Predicate<Response<?>> failedResponsePredicate;

    @Override
    protected <API> Mono<?> handleMonoInvocationSafely(@Nonnull Class<API> type, @Nonnull API service, Method method, Object[] args) {
        return safeInvokeMono(invokeMonoWithMappingExceptions(type, service, method, args), type, service.getClass());
    }

    private <API> Callable<Mono<?>> invokeMonoWithMappingExceptions(Class<API> type, API service, Method method, Object[] args) {
        return () -> ((Mono<?>) method.invoke(service, args))
            .flatMap(responseObj -> responseObj instanceof Response && failedResponsePredicate.test((Response<?>) responseObj)
                ? Mono.error(new HttpException((Response<?>) responseObj))
                : Mono.just(responseObj))
            .onErrorResume((Throwable cause) -> Mono.error(createServiceInvocationExceptionFrom(cause, type)));
    }

    private <API> ServiceInvocationException createServiceInvocationExceptionFrom(Throwable cause, Class<API> service) {
        ServiceInvocationException exception;
        if (cause instanceof ServiceInvocationException) {
            exception = (ServiceInvocationException) cause;
        } else if (cause instanceof ConnectException) {
            exception = new TemporaryServiceInvocationException("Couldn't connect to service", service, cause);
        } else if (cause instanceof SocketTimeoutException) {
            exception = new TemporaryServiceInvocationException("Service invocation timed out", service, cause);
        } else if (cause instanceof IOException) {
            exception = new TemporaryServiceInvocationException("Service invocation failed", service, cause);
        } else if (cause instanceof HttpException) {
            exception = HttpExceptionBasedServiceInvocationExceptionFactory.createFrom((HttpException) cause, service);
        } else {
            exception = new PermanentServiceInvocationException("Unexpected exception during service invocation.", service, cause);
        }
        return exception;
    }
}
