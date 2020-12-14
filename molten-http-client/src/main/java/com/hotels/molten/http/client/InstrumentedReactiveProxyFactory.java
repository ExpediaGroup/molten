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

import java.lang.reflect.Method;
import javax.annotation.Nonnull;

import com.google.common.base.Strings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import reactor.core.publisher.Mono;

import com.hotels.molten.core.metrics.MetricId;

/**
 * Creates proxy which augments reactive method invocations with metrics instrumentation.
 * Registers {@link Timer} metrics under {@code [metricsQualifier].[methodName].[timerMetricsQualifier].[category]}. e.g. {@code client.service.getSomething.raw.success}
 * Registers metrics for successfully emitting an element under {@code success}, and for errors either under {@code error} or {@code failure} for
 * {@link PermanentServiceInvocationException} and {@link TemporaryServiceInvocationException} respectively.
 */
final class InstrumentedReactiveProxyFactory extends AbstractReactiveProxyFactory {
    private final MeterRegistry meterRegistry;
    private final String clientId;
    private final String timerMetricsQualifier;

    InstrumentedReactiveProxyFactory(MeterRegistry meterRegistry, String clientId, String timerMetricsQualifier) {
        checkArgument(meterRegistry != null, "meterRegistry cannot be null.");
        checkArgument(!Strings.isNullOrEmpty(clientId), "clientId cannot be empty.");
        checkArgument(!Strings.isNullOrEmpty(timerMetricsQualifier), "timerMetricsQualifier cannot be empty.");
        this.meterRegistry = meterRegistry;
        this.clientId = clientId;
        this.timerMetricsQualifier = timerMetricsQualifier;
    }

    @Override
    protected <API> Mono<?> handleMonoInvocationSafely(@Nonnull Class<API> type, @Nonnull API service, Method method, Object[] args) {
        Sampler sampler = new Sampler();
        return Mono.defer(() -> {
            sampler.start();
            return safeInvokeMono(invokeMethod(service, method, args), type, service.getClass());
        })
        .doOnSuccess(s -> sampler.success(method))
        .doOnError(e -> sampler.error(method, e));
    }

    private final class Sampler {
        private Sample sample;

        void start() {
            sample = Timer.start(meterRegistry);
        }

        void success(Method method) {
            sample.stop(timerFor(method, "success"));
        }

        void error(Method method, Throwable e) {
            if (e instanceof TemporaryServiceInvocationException) {
                sample.stop(timerFor(method, "failure"));
            } else {
                sample.stop(timerFor(method, "error"));
            }
        }

        private Timer timerFor(Method method, String status) {
            return MetricId.builder()
                .name("http_client_requests")
                .hierarchicalName(hierarchicalName(method, status))
                .tag(Tag.of("client", clientId))
                .tag(Tag.of("endpoint", method.getName()))
                .tag(Tag.of("type", timerMetricsQualifier))
                .tag(Tag.of("status", status))
                .build()
                .toTimer()
                .register(meterRegistry);
        }

        private String hierarchicalName(Method method, String status) {
            return name("client", clientId, method.getName(), timerMetricsQualifier, status);
        }
    }
}
