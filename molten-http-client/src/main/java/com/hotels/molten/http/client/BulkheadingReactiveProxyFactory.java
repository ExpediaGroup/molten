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

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

import com.hotels.molten.metrics.resilience.BulkheadInstrumenter;

/**
 * A reactive proxy factory which adds bulkheads to invocations.
 */
final class BulkheadingReactiveProxyFactory extends AbstractReactiveProxyFactory {
    private final BulkheadConfiguration bulkheadConfiguration;
    private MeterRegistry meterRegistry;
    private String clientId;
    private final Map<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();

    BulkheadingReactiveProxyFactory(BulkheadConfiguration bulkheadConfiguration) {
        this.bulkheadConfiguration = requireNonNull(bulkheadConfiguration);
    }

    @Override
    protected <API> Mono<?> handleMonoInvocationSafely(@Nonnull Class<API> type, @Nonnull API service, Method method, Object[] args) {
        return Mono.just(method)
            .flatMap(m -> Mono.defer(() -> safeInvokeMono(() -> (Mono<?>) method.invoke(service, args), type, service.getClass())))
            .transform(BulkheadOperator.of(getBulkheadForMethod(method)))
            .onErrorResume((Throwable ex) -> Mono.error(convertException(type, method, ex)));
    }

    private <API> Throwable convertException(@Nonnull Class<API> type, Method method, Throwable ex) {
        Throwable exception = ex;
        if (ex instanceof BulkheadFullException) {
            exception = new TemporaryServiceInvocationException(
                "Bulkhead is full for isolationGroup=" + bulkheadConfiguration.getIsolationGroupName() + ", method=" + method.getName(), type, ex);
        }
        return exception;
    }

    private Bulkhead getBulkheadForMethod(Method method) {
        return bulkheads.computeIfAbsent(bulkheadConfiguration.getIsolationGroupName() + "_" + method.getName(),
            bulkheadId -> createBulkhead(bulkheadId, method.getName()));
    }

    private Bulkhead createBulkhead(String bulkheadId, String method) {
        Bulkhead bulkhead = Bulkhead.of(bulkheadId, BulkheadConfig.custom()
            .maxConcurrentCalls(bulkheadConfiguration.getMaxConcurrency())
            .writableStackTraceEnabled(false).build());
        if (meterRegistry != null) {
            BulkheadInstrumenter.builder()
                .meterRegistry(meterRegistry)
                .metricsQualifier("http_client_request_bulkhead")
                .componentTagName("client")
                .componentTagValue(clientId)
                .operationTagName("endpoint")
                .operationTagValue(method)
                .hierarchicalMetricsQualifier(name("client", clientId, method))
                .build()
                .instrument(bulkhead);
        }
        return bulkhead;
    }

    /**
     * Sets the meter registry and client ID to register metrics with.
     *
     * @param meterRegistry the registry
     * @param clientId      the unique ID of the client
     */
    public void setMeterRegistry(MeterRegistry meterRegistry, String clientId) {
        this.meterRegistry = requireNonNull(meterRegistry);
        this.clientId = requireNonNull(clientId);
    }
}
