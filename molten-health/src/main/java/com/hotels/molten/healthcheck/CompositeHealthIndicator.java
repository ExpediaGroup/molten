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

import java.util.Collection;

/**
 * A composite {@link HealthIndicator}. Its health depends on the health of all sub-indicators.
 * Sub-indicators can be added via the {@link CompositeHealthIndicator#watch(HealthIndicator)} method.
 */
public interface CompositeHealthIndicator extends HealthIndicator, HealthIndicatorWatcher {

    /**
     * All the current sub-indicators being watched by this composite.
     *
     * @return all its sub-indicators
     */
    Collection<HealthIndicator> subIndicators();
}
