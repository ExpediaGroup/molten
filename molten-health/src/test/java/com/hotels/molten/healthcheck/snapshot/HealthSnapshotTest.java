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

package com.hotels.molten.healthcheck.snapshot;

import java.time.Duration;
import java.util.List;

import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hotels.molten.healthcheck.Health;
import com.hotels.molten.healthcheck.HealthIndicator;
import com.hotels.molten.healthcheck.SimpleHealthIndicator;
import com.hotels.molten.healthcheck.Status;

/**
 * Unit test for {@link HealthSnapshot}.
 */
public class HealthSnapshotTest {

    @Test
    public void should_create_snapshot_for_composite_component() {
        var testIndicator = HealthIndicator.composite("test");
        var sub1 = new SimpleHealthIndicator("sub1", Health.up());
        var sub2 = new SimpleHealthIndicator("sub2", Health.down("ouch"));
        testIndicator.watch(sub1);
        testIndicator.watch(sub2);

        StepVerifier.withVirtualTime(() -> HealthSnapshot.from(testIndicator))
            .thenAwait(Duration.ofSeconds(1))
            .assertNext(healthSnapshot -> HealthSnapshotAssert.assertThat(healthSnapshot)
                .isSimilarTo(new HealthSnapshot("test", Health.down(Status.DOWN.compositeMessage()), List.of(
                    new HealthSnapshot("sub1", Health.up(), List.of()),
                    new HealthSnapshot("sub2", Health.down("ouch"), List.of())
                ))))
            .verifyComplete();
    }

    @Test
    public void should_wait_and_return_default_up_health() {
        var healthIndicator = new HealthIndicator() {
            @Override
            public String name() {
                return "name";
            }

            @Override
            public Flux<Health> health() {
                return Flux.just(Health.down("ouch")).delayElements(Duration.ofSeconds(1));
            }
        };
        StepVerifier.withVirtualTime(() -> HealthSnapshot.from(healthIndicator))
            .thenAwait(Duration.ofSeconds(1))
            .assertNext(healthSnapshot -> HealthSnapshotAssert.assertThat(healthSnapshot)
                .isSimilarTo(new HealthSnapshot("name", Health.up(), List.of())))
            .verifyComplete();
    }
}
