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
import java.time.Duration;
import javax.annotation.Nonnull;

import com.google.common.base.Strings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import com.hotels.molten.core.metrics.MetricId;

/**
 * Creates a proxy with timeout handling over reactive service.
 */
final class TimeoutReactiveProxyFactory extends AbstractReactiveProxyFactory {
    private final Duration timeout;
    private MeterRegistry meterRegistry;
    private String clientId;
    private String timeoutTypeTag;

    TimeoutReactiveProxyFactory(Duration timeout) {
        checkArgument(timeout.toMillis() > 0, "Timeout must be positive");
        this.timeout = timeout;
    }

    @Override
    protected <API> Mono<?> handleMonoInvocationSafely(@Nonnull Class<API> type, @Nonnull API service, Method method, Object[] args) {
        return safeInvokeMono(invokeMethod(service, method, args), type, service.getClass())
            .timeout(timeout)
            .onErrorResume(e -> {
                Mono res;
                if (e instanceof ServiceInvocationException) {
                    res = Mono.error(e);
                } else {
                    markTimeout(method);
                    res = Mono.error(timeoutException(type, method, e));
                }
                return res;
            });
    }

    private void markTimeout(Method method) {
        if (meterRegistry != null) {
            MetricId.builder()
                .name("http_client_request_timeouts")
                .hierarchicalName(hierarchicalName(method))
                .tag(Tag.of("client", clientId))
                .tag(Tag.of("endpoint", method.getName()))
                .tag(Tag.of("type", timeoutTypeTag))
                .build()
                .toCounter()
                .register(meterRegistry)
                .increment();
        }
    }

    @NotNull
    private String hierarchicalName(Method method) {
        return name("client", clientId, method.getName(), "timeout", timeoutTypeTag);
    }

    private <API> TemporaryServiceInvocationException timeoutException(@Nonnull Class<API> type, Method method, Throwable e) {
        return new TemporaryServiceInvocationException("Service invocation timed out, method=" + method.getName(), type, e);
    }

    TimeoutReactiveProxyFactory withMetrics(MeterRegistry meterRegistry, String clientId, String timeoutTypeTag) {
        checkArgument(meterRegistry != null, "meterRegistry cannot be null.");
        checkArgument(!Strings.isNullOrEmpty(clientId), "clientId cannot be empty.");
        checkArgument(!Strings.isNullOrEmpty(timeoutTypeTag), "timeoutTypeTag cannot be empty.");
        this.meterRegistry = meterRegistry;
        this.clientId = clientId;
        this.timeoutTypeTag = timeoutTypeTag;
        return this;
    }
}
