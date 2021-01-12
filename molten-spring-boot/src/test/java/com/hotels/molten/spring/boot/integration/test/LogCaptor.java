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
package com.hotels.molten.spring.boot.integration.test;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Value;
import org.slf4j.MDC;

public final class LogCaptor extends AppenderBase<ILoggingEvent> {
    private static final List<CapturedLog> CAPTURED_LOGS = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent eventObject) {
        CAPTURED_LOGS.add(new CapturedLog(eventObject));
    }

    public static List<CapturedLog> getCapturedLogs() {
        return CAPTURED_LOGS;
    }

    public static void clearCapturedLogs() {
        CAPTURED_LOGS.clear();
    }

    @Value
    public static final class CapturedLog {
        private final Map<String, String> mdc;
        private final ILoggingEvent event;

        public CapturedLog(ILoggingEvent event) {
            this.event = requireNonNull(event);
            mdc = MDC.getCopyOfContextMap();
        }
    }
}
