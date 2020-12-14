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

import org.reactivestreams.Processor;
import reactor.core.publisher.Flux;

/**
 * Stub for testing {@link HealthIndicator} aware classes.
 */
public final class SimpleHealthIndicator implements HealthIndicator {
    private final String name;
    private Flux<Health> healths;

    public SimpleHealthIndicator(String name, Health... health) {
        this.name = name;
        healths = Flux.fromArray(health).publish().autoConnect();
    }

    public SimpleHealthIndicator(String name, Processor<Health, Health> healthProcessor) {
        this.name = name;
        healths = Flux.from(healthProcessor);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Flux<Health> health() {
        return healths;
    }
}
