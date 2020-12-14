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

import java.time.Clock;
import java.util.concurrent.atomic.LongAdder;

/**
 * Calculates hit ratio with tumbling window metrics. The window is 30 seconds.
 * Keeps metrics from previous window while no events are registered.
 * This solution is thread-safe but not necessarily accurate as there's some race condition at window boundaries.
 */
public final class TumblingWindowHitRatioMetrics {
    private final Aggregation total;
    private final int timeWindowSizeInSeconds = 30;
    private final Clock clock;

    public TumblingWindowHitRatioMetrics() {
        this(Clock.systemDefaultZone());
    }

    TumblingWindowHitRatioMetrics(Clock clock) {
        this.clock = clock;
        total = new Aggregation();
    }

    void registerHit() {
        registerHit(1);
    }

    void registerHit(int hits) {
        moveWindowIfNecessary();
        total.registerHit(hits);
    }

    void registerMiss() {
        registerMiss(1);
    }

    void registerMiss(int misses) {
        moveWindowIfNecessary();
        total.registerMiss(misses);
    }

    double hitRatio() {
        return total.hitRatio();
    }

    private void moveWindowIfNecessary() {
        if (total.epochSeconds < clock.instant().getEpochSecond() - timeWindowSizeInSeconds) {
            total.reset();
        }
    }

    private final class Aggregation {
        private volatile long epochSeconds;
        private final LongAdder hits = new LongAdder();
        private final LongAdder misses = new LongAdder();

        private Aggregation() {
            this.epochSeconds = clock.instant().getEpochSecond();
        }

        synchronized void reset() {
            epochSeconds = clock.instant().getEpochSecond();
            hits.reset();
            misses.reset();
        }

        void registerHit(int num) {
            hits.add(num);
        }

        void registerMiss(int num) {
            misses.add(num);
        }

        double hitRatio() {
            double total = hits.doubleValue() + misses.doubleValue();
            return total > 0 ? hits.doubleValue() / total : 0D;
        }
    }
}
