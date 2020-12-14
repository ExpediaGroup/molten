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

package com.hotels.molten.healthcheck.resilience4j;

import static java.util.Map.entry;

import java.util.Map;
import java.util.function.Function;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;

import com.hotels.molten.healthcheck.Health;
import com.hotels.molten.healthcheck.HealthIndicator;

/**
 * Reports health-check based on the events emitted by the circuit breaker.
 *
 * <p>The last emitted {@link Health} is replayed to each subscriber even for the new ones. The first emitted
 * {@link Health} is based on the current state of the circuit breaker. It is emitted only once, regardless of the number of subscribers.</p>
 */
public class HealthIndicatorOverCircuitBreaker implements HealthIndicator {
    private static final String FIRST_MESSAGE = "Last state transition is not known";
    private static final Map<CircuitBreaker.State, Function<String, Health>> STATE_TO_HEALTH = Map.ofEntries(
        entry(CircuitBreaker.State.OPEN, Health::down),
        entry(CircuitBreaker.State.FORCED_OPEN, Health::down),
        entry(CircuitBreaker.State.HALF_OPEN, Health::struggling),
        entry(CircuitBreaker.State.CLOSED, Health::up),
        entry(CircuitBreaker.State.DISABLED, Health::up),
        entry(CircuitBreaker.State.METRICS_ONLY, Health::up)
    );

    private final String name;
    private final ConnectableFlux<Health> memoizeLastHealth;

    public HealthIndicatorOverCircuitBreaker(CircuitBreaker circuitBreaker) {
        name = circuitBreaker.getName();
        Flux<Health> stateTransitions = Flux.from(subscriber ->
            circuitBreaker.getEventPublisher()
                .onStateTransition(event -> subscriber.onNext(STATE_TO_HEALTH.get(event.getStateTransition().getToState()).apply(event.toString()))
                ));
        memoizeLastHealth = Flux.just(STATE_TO_HEALTH.get(circuitBreaker.getState()).apply(FIRST_MESSAGE))
            .concatWith(stateTransitions)
            .replay(1);
        memoizeLastHealth.connect();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Flux<Health> health() {
        return memoizeLastHealth;
    }
}
