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

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.Reflection;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Creates a proxy over reactive service.
 * Supports only {@link Mono} return type.
 */
abstract class AbstractReactiveProxyFactory {
    private static final boolean REACTOR_TRACE_MODE_ON = "true".equalsIgnoreCase(System.getProperty("MOLTEN_HTTP_CLIENT_REACTOR_TRACE_LOG_ENABLED"));

    /**
     * Wraps the a reactive service in a proxy with custom logic.
     *
     * @param type    the actual type of the service
     * @param service the service instance
     * @param <API>   the type of the service
     * @return the proxy, never null
     * @throws IllegalArgumentException if the service has an incompatible API
     */
    <API> API wrap(@Nonnull Class<API> type, @Nonnull API service) {
        checkArgument(type != null && service != null, "Service type and instance cannot be null");
        validateServiceApi(type);
        return Reflection.newProxy(type, new AbstractInvocationHandler() {
            @Override
            protected Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {
                Object result;
                if (Mono.class.isAssignableFrom(method.getReturnType())) {
                    result = safeInvokeMono(() -> handleMonoInvocationSafely(type, service, method, args), type, service.getClass());
                } else {
                    throw new IllegalArgumentException("Method return type is not supported '" + method.getReturnType() + "'");
                }
                return result;
            }
        });
    }

    /**
     * Handles the method invocation in a type-safe manner.
     * This method should never throw an exception but return it as {@link Mono#error(Throwable)}. If it still happens then rather a
     * {@link PermanentServiceInvocationException} will be emitted with the thrown exception as cause.
     *
     * @param type    the type of the API
     * @param service an actual instance of the API
     * @param method  the method to be invoked
     * @param args    the current method arguments
     * @param <API>   the API type
     * @return the result that should be returned
     */
    protected abstract <API> Mono<?> handleMonoInvocationSafely(@Nonnull Class<API> type, @Nonnull API service, Method method, Object[] args);

    /**
     * Invokes a reactive callable safely by handling unexpected exceptions.
     *
     * @param callable   the callable to invoke
     * @param apiType    the API type
     * @param actualType the actual type
     * @return the invocation result
     */
    protected Mono<?> safeInvokeMono(Callable<Mono<?>> callable, Class<?> apiType, Class<?> actualType) {
        Mono<?> result;
        try {
            if (REACTOR_TRACE_MODE_ON) {
                result = callable.call().log();
            } else {
                result = callable.call();
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(apiType)
                .warn("Unexpected exception during service invocation. The service implementation should never throw an exception but emit it. Type={}", actualType, e);
            result = Mono.error(new PermanentServiceInvocationException("Unexpected exception during service invocation", apiType, Optional.ofNullable(e.getCause()).orElse(e)));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    protected <API, RESULT> Callable<RESULT> invokeMethod(API service, Method method, Object[] args) {
        return () -> (RESULT) method.invoke(service, args);
    }

    /**
     * Verifies that the type has only methods with {@link Mono} return type.
     *
     * @param type  the class to check
     * @param <API> the actual type
     * @throws IllegalArgumentException if the type is not acceptable
     */
    private <API> void validateServiceApi(Class<API> type) {
        Stream<Class<?>> returnTypes = Arrays.stream(type.getMethods()).map(Method::getReturnType);
        if (!returnTypes.allMatch(returnType -> returnType.isAssignableFrom(Mono.class))) {
            throw new IllegalArgumentException("The wrapped service type must have only methods with Mono return type.");
        }
    }
}
