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

package com.hotels.molten.core.log;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;

/**
 * Delegates logs to the SLF4J {@link org.slf4j.Logger} with adjustable {@link com.hotels.molten.core.log.LogLevel}.
 */
@RequiredArgsConstructor
public class DelegatingLogger {
    private final Logger logger;

    public void log(LogLevel logLevel, String message, Object... objects) {
        logLevel.log(logger, message, objects);
    }
}
