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

package com.hotels.molten.http.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.mockito.ArgumentMatcher;

/**
 * Test helper for logging.
 */
public final class LogMatcher {

    /**
     * Creates matcher to assert log level and message.
     *
     * @param level   the expected level
     * @param messageMatcher the expected message as regexp pattern
     * @return the matcher
     */
    public static ArgumentMatcher<ILoggingEvent> matchesLogEvent(Level level, Matcher<String> messageMatcher) {
        return new ArgumentMatcher<ILoggingEvent>() {
            @Override
            public boolean matches(ILoggingEvent loggingEvent) {
                return loggingEvent != null && loggingEvent.getLevel().equals(level) && messageMatcher.matches(loggingEvent.getFormattedMessage());
            }

            @Override
            public String toString() {
                return "log with level " + level + " and message:" + StringDescription.toString(messageMatcher);
            }
        };
    }
}
