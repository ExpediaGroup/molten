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

import reactor.core.publisher.Flux;

/**
 * A service/component that has a health which is reported via this interface.
 */
public interface HealthIndicator {

    /**
     * The name of this component.
     *
     * @return the name
     */
    String name();

    /**
     * The stream of the health status.
     *
     * @return the stream of the health status
     */
    Flux<Health> health();

    /**
     * Creates a component which health depends on the sub-components' health.
     *
     * @param name the component name
     * @return the composite component
     */
    static CompositeHealthIndicator composite(String name) {
        return new CompositeComponent(name, Status.UP);
    }

    /**
     * Creates a component which health depends on the sub-components' health.
     *
     * @param name the component name
     * @param statusWhenEmpty default status to report when there are no sub components
     * @return the composite component
     */
    static CompositeHealthIndicator composite(String name, Status statusWhenEmpty) {
        return new CompositeComponent(name, statusWhenEmpty);
    }
}
