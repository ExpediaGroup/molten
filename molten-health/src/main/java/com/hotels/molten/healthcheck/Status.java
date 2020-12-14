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
 * Health status.
 */
public enum Status {

    UP("All downstream components are fine"),
    STRUGGLING("One or more downstream components are struggling"),
    DOWN("One or more downstream components are down");

    private final String compositeMessage;

    Status(String compositeMessage) {
        this.compositeMessage = compositeMessage;
    }

    /**
     * The message of a {@link CompositeHealthIndicator}'s status.
     *
     * @return the composite message
     */
    public String compositeMessage() {
        return compositeMessage;
    }
}
