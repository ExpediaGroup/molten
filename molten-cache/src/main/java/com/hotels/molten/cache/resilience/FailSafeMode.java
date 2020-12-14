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

package com.hotels.molten.cache.resilience;

/**
 * Different fail-safe modes.
 */
public enum FailSafeMode {
    /**
     * Not fail-safe.
     */
    NOPE(false),
    /**
     * Fail-safe without logging.
     */
    SILENT(true),
    /**
     * Fail-safe with logging but without stack trace.
     */
    LOGGING(true),
    /**
     * Fail-safe with logging with stack trace.
     */
    VERBOSE(true);

    private final boolean failsafe;

    FailSafeMode(boolean failsafe) {
        this.failsafe = failsafe;
    }

    public boolean isFailsafe() {
        return failsafe;
    }
}
