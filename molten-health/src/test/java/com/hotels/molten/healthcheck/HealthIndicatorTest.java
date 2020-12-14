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

import static org.assertj.core.api.Assertions.assertThat;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hotels.molten.test.AssertSubscriber;

/**
 * Unit test for {@link HealthIndicator}.
 */
public class HealthIndicatorTest {
    private CompositeHealthIndicator composite;

    @BeforeMethod
    public void setUp() {
        composite = HealthIndicator.composite("test");
    }

    @Test
    public void health_should_be_the_composite_of_sub_components() {
        StepVerifier.create(composite.health().next())
            .consumeNextWith(health -> HealthAssert.assertThat(health).hasStatus(Status.UP))
            .verifyComplete();
        var sub1 = new SimpleHealthIndicator("sub1", Health.up());
        composite.watch(sub1);
        StepVerifier.create(composite.health().next())
            .consumeNextWith(health -> HealthAssert.assertThat(health).hasStatus(Status.UP))
            .verifyComplete();
        var sub2 = new SimpleHealthIndicator("sub2", Health.down("ouch"));
        composite.watch(sub2);
        StepVerifier.create(composite.health().next())
            .consumeNextWith(health -> HealthAssert.assertThat(health).isSameExceptTimestamp(Health.down("One or more downstream components are down")))
            .verifyComplete();
    }

    @Test
    public void watched_indicators_should_be_returned() {
        assertThat(composite.subIndicators()).isEmpty();
        var sub1 = new SimpleHealthIndicator("sub1", Health.up());
        composite.watch(sub1);
        assertThat(composite.subIndicators()).contains(sub1);
        var sub2 = new SimpleHealthIndicator("sub2", Health.down("ouch"));
        composite.watch(sub2);
        assertThat(composite.subIndicators()).containsExactlyInAnyOrder(sub1, sub2);
    }

    @Test
    public void unwatched_indicators_should_be_removed() {
        var sub1 = new SimpleHealthIndicator("sub1", Health.up());
        var sub2 = new SimpleHealthIndicator("sub2", Health.down("ouch"));
        composite.watch(sub1);
        composite.watch(sub2);
        assertThat(composite.subIndicators()).containsExactlyInAnyOrder(sub1, sub2);
        composite.unwatch(sub1);
        assertThat(composite.subIndicators()).contains(sub2);
    }

    @Test
    public void subindicators_should_be_empty_if_none_is_watched() {
        assertThat(composite.subIndicators()).isEmpty();
    }

    @Test
    public void composite_should_be_healthy_without_sub_components() {
        StepVerifier.create(composite.health().next())
            .expectNextMatches(Health::healthy)
            .verifyComplete();
    }

    @Test
    public void should_replay_last_emitted_item() {
        composite.watch(up1());
        StepVerifier.create(composite.health().next())
            .expectNextMatches(Health::healthy)
            .verifyComplete();
        StepVerifier.create(composite.health().next())
            .expectNextMatches(Health::healthy)
            .verifyComplete();
    }

    @Test
    public void should_take_aggregated_health_of_sub_indicator() {
        composite.watch(up1());
        StepVerifier.create(composite.health().next())
            .expectNextMatches(Health::healthy)
            .verifyComplete();
    }

    @Test
    public void should_take_latest_health_of_sub_indicator() {
        composite.watch(upDown());
        StepVerifier.create(composite.health().next())
            .expectNextMatches(health -> health.status() == Status.DOWN)
            .verifyComplete();
    }

    @Test
    public void should_take_most_severe_health_of_sub_indicators() {
        composite.watch(up1());
        composite.watch(down());
        StepVerifier.create(composite.health().next())
            .expectNextMatches(health -> health.status() == Status.DOWN)
            .verifyComplete();
    }

    @Test
    public void should_take_latest_health_from_newly_watched_indicator() {
        composite.watch(up1());
        var testSubscriber1 = AssertSubscriber.<Health>create();
        composite.health().subscribe(testSubscriber1);
        var testSubscriber2 = AssertSubscriber.<Health>create();
        composite.watch(down());
        composite.health().subscribe(testSubscriber2);

        testSubscriber1.assertValues(values -> assertThat(values)
            .extracting(Health::status)
            .contains(Status.UP, Status.DOWN));
        testSubscriber2.assertValues(values -> assertThat(values)
            .extracting(Health::status)
            .contains(Status.DOWN));
    }

    @Test
    public void should_emit_ok_even_without_emitted_events() {
        composite.watch(new SimpleHealthIndicator("1"));
        StepVerifier.create(composite.health().next())
            .expectNextMatches(Health::healthy)
            .verifyComplete();
    }

    @Test
    public void should_calculate_health_from_sub_indicators() {
        composite.watch(up1());
        composite.watch(up2());
        StepVerifier.create(composite.health().next())
            .expectNextMatches(Health::healthy)
            .verifyComplete();
    }

    @Test
    public void should_notify_multiple_subscribers_about_distinct_healthy_events() {
        Flux<Health> healths = composite.health();
        var testSubscriber1 = AssertSubscriber.<Health>create();
        healths.subscribe(testSubscriber1);
        var testSubscriber2 = AssertSubscriber.<Health>create();
        healths.subscribe(testSubscriber2);
        composite.watch(up1());
        composite.watch(up2());
        testSubscriber1.dispose();
        composite.watch(down());
        testSubscriber1.assertValues(values -> assertThat(values)
            .extracting(Health::status)
            .contains(Status.UP));
        testSubscriber2.assertValues(values -> assertThat(values)
            .extracting(Health::status)
            .contains(Status.UP, Status.DOWN));
    }

    private HealthIndicator up1() {
        return new SimpleHealthIndicator("simple1", Health.up());
    }

    private HealthIndicator up2() {
        return new SimpleHealthIndicator("simple2", Health.up());
    }

    private HealthIndicator down() {
        return new SimpleHealthIndicator("down", Health.down("ouch"));
    }

    private HealthIndicator upDown() {
        return new SimpleHealthIndicator("up_and_down", Health.up(), Health.down("ouch"));
    }
}
