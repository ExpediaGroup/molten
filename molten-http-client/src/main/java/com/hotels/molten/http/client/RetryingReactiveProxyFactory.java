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
import static com.hotels.molten.core.metrics.MetricsSupport.name;
import static java.util.Objects.requireNonNull;

import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import com.hotels.molten.core.metrics.MetricId;

/**
 * Creates a proxy over services which retries method invocation if they emit a {@link TemporaryServiceInvocationException}.
 * If {@code metricsRegistry} is provided, also reports retry metrics as meter per method per retry under {@code [metricsQualifier].[methodName].retries.retry[nthRetry]}
 */
class RetryingReactiveProxyFactory extends AbstractReactiveProxyFactory {
    private final int retries;
    private final String clientId;
    private MeterRegistry meterRegistry;

    RetryingReactiveProxyFactory(int retries, String clientId) {
        this.clientId = requireNonNull(clientId);
        checkArgument(retries > 0, "Number of retries must be positive.");
        this.retries = retries;
    }

    @Override
    protected <API> Mono<?> handleMonoInvocationSafely(@Nonnull Class<API> type, @Nonnull API service, Method method, Object[] args) {
        CallLog callLog = new CallLog(clientId, type, method);
        return Mono.defer(() -> {
            callLog.start();
            return safeInvokeMono(invokeMethod(service, method, args), type, service.getClass());
        })
            .retryWhen(Retry
                .max(retries)
                .filter(ex -> ex instanceof TemporaryServiceInvocationException)
                .doBeforeRetry(rs -> retryLoggerFor(type, method, callLog).accept(rs.failure(), rs.totalRetries())))
            .onErrorMap(Exceptions::isRetryExhausted, Throwable::getCause)
            .doOnError(callLog::fail);
    }

    private <API> BiConsumer<Throwable, Long> retryLoggerFor(@Nonnull Class<API> type, Method method, CallLog callLog) {
        return (e, totalRetries) -> {
            int retryIndex = totalRetries.intValue() + 1;
            callLog.failWithRetry(e, retryIndex);
            LoggerFactory.getLogger(type).debug("Retrying (#{}) execution of target={}#{}", retryIndex, type.getName(), method.getName());
            if (meterRegistry != null) {
                MetricId.builder()
                    .name("http_client_request_retries")
                    .hierarchicalName(hierarchicalName(method, retryIndex))
                    .tag(Tag.of("client", clientId))
                    .tag(Tag.of("endpoint", method.getName()))
                    .tag(Tag.of("retry", "retry" + retryIndex))
                    .build()
                    .toCounter()
                    .register(meterRegistry)
                    .increment();
            }
        };
    }

    private String hierarchicalName(Method method, int retryIndex) {
        return name("client", clientId, method.getName(), "retries", "retry" + retryIndex);
    }

    RetryingReactiveProxyFactory withMetrics(MeterRegistry meterRegistry) {
        checkArgument(meterRegistry != null, "meterRegistry cannot be null.");
        this.meterRegistry = meterRegistry;
        return this;
    }
}
