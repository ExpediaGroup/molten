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

package com.hotels.molten.healthcheck;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.TimeUnit;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import reactor.core.publisher.Flux;

import com.hotels.molten.healthcheck.resilience4j.HealthIndicatorOverCircuitBreaker;

/**
 * A health indicator which relies on a circuit-breaker to gather statistics of a certain flow.
 * The flow can record successful and failed stages which events are delegated to the circuit-breaker as success and error events respectively.
 * The health of this indicator is based on the circuit's state according to {@link HealthIndicatorOverCircuitBreaker}.
 */
public final class CircuitBasedFlowMarker implements HealthIndicator, FlowMarker {
    private final String name;
    private final CircuitBreaker circuitBreaker;
    private final HealthIndicatorOverCircuitBreaker indicatorOverCircuitBreaker;

    public CircuitBasedFlowMarker(String name, CircuitBreakerConfig circuitBreakerConfig) {
        this.name = requireNonNull(name);
        this.circuitBreaker = CircuitBreaker.of("health-" + name, CircuitBreakerConfig.from(requireNonNull(circuitBreakerConfig))
            .enableAutomaticTransitionFromOpenToHalfOpen().build());
        indicatorOverCircuitBreaker = new HealthIndicatorOverCircuitBreaker(circuitBreaker);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Flux<Health> health() {
        return indicatorOverCircuitBreaker.health();
    }

    @Override
    public void success() {
        circuitBreaker.onSuccess(0, TimeUnit.MILLISECONDS);
    }

    @Override
    public void failure(Throwable ex) {
        circuitBreaker.onError(0, TimeUnit.MILLISECONDS, ex);
    }

}
