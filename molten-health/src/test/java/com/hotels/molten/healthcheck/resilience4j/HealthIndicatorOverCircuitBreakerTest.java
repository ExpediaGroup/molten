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

import static com.hotels.molten.healthcheck.HealthAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.core.EventConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import com.hotels.molten.healthcheck.Status;

/**
 * Unit test for {@link HealthIndicatorOverCircuitBreaker}.
 */
@ExtendWith(MockitoExtension.class)
public class HealthIndicatorOverCircuitBreakerTest {
    private static final String A_NAME = "any";
    @Mock
    private CircuitBreaker circuitBreaker;
    @Mock
    private CircuitBreaker.EventPublisher eventPublisher;
    @Captor
    private ArgumentCaptor<EventConsumer<CircuitBreakerOnStateTransitionEvent>> eventConsumerCaptor;
    private HealthIndicatorOverCircuitBreaker indicator;

    @BeforeEach
    public void setUp() {
        when(circuitBreaker.getEventPublisher()).thenReturn(eventPublisher);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(circuitBreaker.getName()).thenReturn(A_NAME);
        indicator = new HealthIndicatorOverCircuitBreaker(circuitBreaker);
    }

    @Test
    public void should_return_name() {
        assertThat(indicator.name()).isEqualTo(A_NAME);
    }

    @Test
    public void should_emit_latest_state() {
        verify(eventPublisher).onStateTransition(eventConsumerCaptor.capture());
        StepVerifier.create(indicator.health().take(2))
            .assertNext(n -> assertThat(n).hasStatus(Status.UP))
            .then(() -> eventConsumerCaptor.getValue().consumeEvent(new CircuitBreakerOnStateTransitionEvent(A_NAME, CircuitBreaker.StateTransition.CLOSED_TO_OPEN)))
            .assertNext(n -> assertThat(n).hasStatus(Status.DOWN))
            .verifyComplete();
    }

    @Test
    public void should_half_open_state_emit_struggling() {
        verify(eventPublisher).onStateTransition(eventConsumerCaptor.capture());
        StepVerifier.create(indicator.health().take(4))
            .assertNext(n -> assertThat(n).hasStatus(Status.UP))
            .then(() -> eventConsumerCaptor.getValue().consumeEvent(new CircuitBreakerOnStateTransitionEvent(A_NAME, CircuitBreaker.StateTransition.CLOSED_TO_OPEN)))
            .assertNext(n -> assertThat(n).hasStatus(Status.DOWN))
            .then(() -> eventConsumerCaptor.getValue().consumeEvent(new CircuitBreakerOnStateTransitionEvent(A_NAME, CircuitBreaker.StateTransition.OPEN_TO_HALF_OPEN)))
            .assertNext(n -> assertThat(n).hasStatus(Status.STRUGGLING))
            .then(() -> eventConsumerCaptor.getValue().consumeEvent(new CircuitBreakerOnStateTransitionEvent(A_NAME, CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED)))
            .assertNext(n -> assertThat(n).hasStatus(Status.UP))
            .verifyComplete();
    }

    @Test
    public void should_health_based_on_state_for_the_first_time() {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        indicator = new HealthIndicatorOverCircuitBreaker(circuitBreaker);
        StepVerifier.create(indicator.health().take(1))
            .assertNext(n -> assertThat(n).hasStatus(Status.DOWN))
            .verifyComplete();
    }
}
