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

package com.hotels.molten.healthcheck.snapshot;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.hotels.molten.healthcheck.CompositeHealthIndicator;
import com.hotels.molten.healthcheck.Health;
import com.hotels.molten.healthcheck.HealthIndicator;

/**
 * Creates a snapshot from the given {@link HealthIndicator}. Gathers the first {@link Health} elements emitted by {@link HealthIndicator#health()}.
 *
 * <p>It handles {@link CompositeHealthIndicator}, so its result properly reflects the hierarchy of the given {@link HealthIndicator}.</p>
 */
@Value
@Slf4j
public final class HealthSnapshot {
    @NonNull
    private final String name;
    @NonNull
    private final Health health;
    @NonNull
    private final Collection<HealthSnapshot> components;

    /**
     * Creates the snapshot from the given indicator.
     *
     * @param healthIndicator to create the snapshot for
     * @return the current snapshot
     */
    public static Mono<HealthSnapshot> from(HealthIndicator healthIndicator) {
        Mono<List<HealthSnapshot>> subComponents = Mono.just(List.of());
        if (healthIndicator instanceof CompositeHealthIndicator) {
            subComponents = Flux.fromIterable(((CompositeHealthIndicator) healthIndicator).subIndicators())
                .flatMap(HealthSnapshot::from)
                .collectList()
                .timeout(Duration.ofMillis(100))
                .doOnError(throwable -> LOG.warn("Health-check snapshot could not be properly created for health_indicator={}", healthIndicator.name(), throwable))
                .onErrorReturn(List.of());
        }
        return healthIndicator.health()
            .take(Duration.ofMillis(100))
            .next()
            .defaultIfEmpty(Health.up())
            .zipWith(subComponents, (h, c) -> new HealthSnapshot(healthIndicator.name(), h, c));
    }
}
