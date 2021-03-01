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

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Unit test for {@link CircuitBasedFlowMarker}.
 */
public class CircuitBasedFlowMarkerTest {
    private CircuitBasedFlowMarker indicator;

    @BeforeEach
    public void initContext() {
        indicator = new CircuitBasedFlowMarker("component", CircuitBreakerConfig.custom()
            .slidingWindow(2, 1, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMillis(200))
            .permittedNumberOfCallsInHalfOpenState(1)
            .build());
    }

    @Test
    public void should_report_healthy_initially() {
        StepVerifier.create(indicator.health().next())
            .consumeNextWith(health -> HealthAssert.assertThat(health).hasStatus(Status.UP))
            .verifyComplete();
    }

    @Test
    public void should_report_down_once_cb_opens() {
        StepVerifier.create(indicator.health().take(2))
            .consumeNextWith(health -> HealthAssert.assertThat(health).hasStatus(Status.UP))
            .then(() -> indicator.failure(new IllegalStateException()))
            .consumeNextWith(health -> HealthAssert.assertThat(health).hasStatus(Status.DOWN))
            .verifyComplete();
    }

    @Test
    public void should_report_healthy_once_cb_closes() {
        StepVerifier.create(indicator.health().take(4))
            .consumeNextWith(health -> HealthAssert.assertThat(health).hasStatus(Status.UP))
            .then(() -> indicator.failure(new IllegalStateException()))
            .consumeNextWith(health -> HealthAssert.assertThat(health).hasStatus(Status.DOWN))
            .thenAwait(Duration.ofMillis(300))
            .then(() -> indicator.success())
            .consumeNextWith(health -> HealthAssert.assertThat(health).hasStatus(Status.STRUGGLING))
            .then(() -> indicator.success())
            .consumeNextWith(health -> HealthAssert.assertThat(health).hasStatus(Status.UP))
            .verifyComplete();
    }
}
