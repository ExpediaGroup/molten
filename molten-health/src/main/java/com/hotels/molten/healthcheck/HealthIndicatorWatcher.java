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

/**
 * Watches/monitors the given indicator(s).
 */
@FunctionalInterface
public interface HealthIndicatorWatcher {

    /**
     * Watch the given indicator.
     *
     * @param indicator that should be watched
     */
    void watch(HealthIndicator indicator);

    /**
     * Unwatch the given indicator.
     *
     * @param indicator that should be unwatched
     */
    default void unwatch(HealthIndicator indicator) {
        throw new UnsupportedOperationException("Cannot unwatch indicator");
    }
}
