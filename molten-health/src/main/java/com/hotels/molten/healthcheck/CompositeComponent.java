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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.google.common.collect.Ordering;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ReplayProcessor;

/**
 * A {@link CompositeHealthIndicator} implementation that returns
 * the highest severity STATUS of the downstream services.
 */
final class CompositeComponent implements CompositeHealthIndicator {
    private final String name;
    private final Status statusWhenEmpty;
    private final Map<String, SubscribedHealthIndicator> subIndicators = new ConcurrentHashMap<>();
    private final ReplayProcessor<Health> compositeHealth;
    private final Flux<Health> healthChanges;

    CompositeComponent(String name, Status statusWhenEmpty) {
        this.name = requireNonNull(name);
        this.statusWhenEmpty = requireNonNull(statusWhenEmpty);
        this.compositeHealth = ReplayProcessor.cacheLastOrDefault(Health.builder(statusWhenEmpty).withMessage(statusWhenEmpty.compositeMessage()).build());
        healthChanges = compositeHealth
            .distinctUntilChanged(Health::status)
            .replay(1)
            .autoConnect();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Flux<Health> health() {
        return healthChanges;
    }

    @Override
    public Collection<HealthIndicator> subIndicators() {
        return subIndicators.values().stream().map(e -> e.healthIndicator).collect(Collectors.toList());
    }

    @Override
    public void watch(HealthIndicator indicator) {
        var subscribedHealthIndicator = new SubscribedHealthIndicator(indicator);
        // we deduplicate indicators by their name, unwatching existing ones
        subIndicators.compute(indicator.name(), (name, existingIndicator) -> {
            if (existingIndicator != null) {
                existingIndicator.dispose();
            }
            return subscribedHealthIndicator;
        });
        subscribedHealthIndicator.startWatch();
    }

    @Override
    public void unwatch(HealthIndicator indicator) {
        var existingIndicator = subIndicators.remove(indicator.name());
        if (existingIndicator != null) {
            existingIndicator.dispose();
        }
    }

    private void emitAggregatedHealthChange() {
        Status overallStatus = subIndicators.values().stream()
            .map(s -> s.latestHealth)
            .map(Health::status)
            .max(Ordering.explicit(Status.UP, Status.STRUGGLING, Status.DOWN))
            .orElse(statusWhenEmpty);
        compositeHealth.onNext(Health.builder(overallStatus).withMessage(overallStatus.compositeMessage()).build());
    }

    private final class SubscribedHealthIndicator {
        private final HealthIndicator healthIndicator;
        private volatile Disposable subscription;
        private volatile Health latestHealth;

        private SubscribedHealthIndicator(HealthIndicator healthIndicator) {
            this.healthIndicator = healthIndicator;
        }

        private void startWatch() {
            subscription = healthIndicator.health().subscribe(health -> {
                latestHealth = health;
                emitAggregatedHealthChange();
            });
        }

        private void dispose() {
            if (subscription != null) {
                subscription.dispose();
                subscription = null;
            }
        }
    }
}
