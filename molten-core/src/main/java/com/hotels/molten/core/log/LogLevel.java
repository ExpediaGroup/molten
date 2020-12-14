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

import java.util.function.BiConsumer;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;

/**
 * Represent the SLF4J log levels and do the logging with them it should be used from the {@link com.hotels.molten.core.log.DelegatingLogger}.
 */
@RequiredArgsConstructor
public enum LogLevel {
    TRACE(logger -> logger::trace),
    INFO(logger -> logger::info),
    DEBUG(logger -> logger::debug),
    WARN(logger -> logger::warn),
    ERROR(logger -> logger::error);

    private final Function<Logger, BiConsumer<String, Object[]>> loggerFunction;

    void log(Logger logger, String message, Object... objects) {
        loggerFunction.apply(logger).accept(message, objects);
    }
}
