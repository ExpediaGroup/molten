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

package com.hotels.molten.cache.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.hotels.molten.test.TestClock;

/**
 * Unit test for {@link TumblingWindowHitRatioMetrics}.
 */
public class TumblingWindowHitRatioMetricsTest {
    private TumblingWindowHitRatioMetrics metrics;
    private TestClock clock = TestClock.get();

    @BeforeMethod
    public void initContext() {
        metrics = new TumblingWindowHitRatioMetrics(clock);
    }

    @Test
    public void shouldCalculateRatioAsExpected() {
        metrics.registerHit();
        assertThat(metrics.hitRatio()).isEqualTo(1D);
        metrics.registerMiss();
        assertThat(metrics.hitRatio()).isEqualTo(0.5D);
        metrics.registerHit();
        assertThat(metrics.hitRatio()).isBetween(0.65D, 0.67D);
    }

    @Test
    public void shouldCalculateRatioAsExpectedWhenTimeChanges() {
        metrics.registerHit();
        metrics.registerHit();
        metrics.registerMiss();
        metrics.registerHit();
        assertThat(metrics.hitRatio()).isEqualTo(0.75D);
        clock.advanceTimeBy(Duration.ofSeconds(10));
        assertThat(metrics.hitRatio()).isEqualTo(0.75D);
        clock.advanceTimeBy(Duration.ofSeconds(20));
        // at this point we reached the time window
        assertThat(metrics.hitRatio()).isEqualTo(0.75D);
        clock.advanceTimeBy(Duration.ofSeconds(10));
        // at this point we are in a new window but metrics are kept since no events were registered
        assertThat(metrics.hitRatio()).isEqualTo(0.75D);
        // window is reset at this point
        metrics.registerHit();
        assertThat(metrics.hitRatio()).isEqualTo(1D);

    }
}
