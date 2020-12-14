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

package com.hotels.molten.test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

import lombok.Builder;
import lombok.Builder.Default;

/**
 * Test clock to manually adjust time.
 */
@Builder(toBuilder = true)
public class TestClock extends Clock {
    @Default
    private Instant instant = Instant.ofEpochMilli(0);
    @Default
    private ZoneId zone = ZoneId.systemDefault();

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this.toBuilder().zone(zone).build();
    }

    @Override
    public Instant instant() {
        return instant;
    }

    /**
     * Gets the current time according to this clock.
     *
     * @return now
     */
    public Instant now() {
        return instant();
    }

    /**
     * Advance time by fixed amount.
     *
     * @param time the amount to add
     * @param unit the unit of the amount to add
     */
    public void advanceTimeBy(long time, TemporalUnit unit) {
        instant = instant.plus(time, unit);
    }

    /**
     * Advance time by fixed amount.
     *
     * @param duration the amount to add
     */
    public void advanceTimeBy(Duration duration) {
        instant = instant.plus(duration.toMillis(), ChronoUnit.MILLIS);
    }

    /**
     * Creates a new test clock with defaults.
     *
     * @return the test clock
     */
    public static TestClock get() {
        return TestClock.builder().build();
    }
}
