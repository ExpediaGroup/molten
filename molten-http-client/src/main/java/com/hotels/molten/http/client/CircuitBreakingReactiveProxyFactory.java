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

import static com.hotels.molten.core.metrics.MetricsSupport.name;
import static java.util.Objects.requireNonNull;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

import com.hotels.molten.healthcheck.HealthIndicatorWatcher;
import com.hotels.molten.healthcheck.resilience4j.HealthIndicatorOverCircuitBreaker;
import com.hotels.molten.metrics.resilience.CircuitBreakerInstrumenter;

/**
 * A reactive proxy factory which adds circuit-breaker to invocations.
 *
 * Please note that the provided {@link CircuitBreakerConfig} will be altered in a way it acts only on {@link TemporaryServiceInvocationException} as failures.
 */
final class CircuitBreakingReactiveProxyFactory extends AbstractReactiveProxyFactory {
    private final String clientId;
    private final CircuitBreakerConfig config;
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final HealthIndicatorWatcher healthIndicatorWatcher;
    private MeterRegistry meterRegistry;

    CircuitBreakingReactiveProxyFactory(String clientId, CircuitBreakerConfig config, HealthIndicatorWatcher healthIndicatorWatcher) {
        this.clientId = requireNonNull(clientId);
        this.config = requireNonNull(config);
        this.healthIndicatorWatcher = requireNonNull(healthIndicatorWatcher);
    }

    @Override
    protected <API> Mono<?> handleMonoInvocationSafely(@Nonnull Class<API> type, @Nonnull API service, Method method, Object[] args) {
        return Mono.just(method)
            .flatMap(m -> Mono.defer(() -> safeInvokeMono(() -> (Mono<?>) method.invoke(service, args), type, service.getClass())))
            .transform(CircuitBreakerOperator.of(getCircuitBreakerForMethod(method)))
            .onErrorResume((Throwable ex) -> Mono.error(convertException(type, method, ex)));
    }

    private <API> Throwable convertException(@Nonnull Class<API> type, Method method, Throwable ex) {
        Throwable exception = ex;
        if (ex instanceof CallNotPermittedException) {
            exception = new TemporaryServiceInvocationException(
                "CircuitBreaker is open for isolationGroup=" + clientId + ", method=" + method.getName(), type, ex);
        }
        return exception;
    }

    private CircuitBreaker getCircuitBreakerForMethod(Method method) {
        return circuitBreakers.computeIfAbsent(clientId + "_" + method.getName(),
            circuitBreakerId -> createCircuitBreaker(circuitBreakerId, method.getName()));
    }

    private CircuitBreaker createCircuitBreaker(String circuitBreakerId, String methodName) {
        CircuitBreaker circuitBreaker = CircuitBreaker.of(circuitBreakerId, CircuitBreakerConfig.from(config)
            .recordException(exception -> exception instanceof TemporaryServiceInvocationException)
            .build());
        if (meterRegistry != null) {
            CircuitBreakerInstrumenter.builder()
                .meterRegistry(meterRegistry)
                .metricsQualifier("http_client_request_circuit")
                .hierarchicalMetricsQualifier(name("client", clientId, methodName))
                .componentTagName("client")
                .componentTagValue(clientId)
                .operationTagName("endpoint")
                .operationTagValue(methodName)
                .build().instrument(circuitBreaker);
        }
        healthIndicatorWatcher.watch(new HealthIndicatorOverCircuitBreaker(circuitBreaker));
        return circuitBreaker;
    }

    /**
     * Sets the meter registry and qualifier to register metrics with.
     *
     * @param meterRegistry   the registry
     */
    void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = requireNonNull(meterRegistry);
    }
}
