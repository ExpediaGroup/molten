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

package com.hotels.molten.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;

import io.lettuce.core.event.Event;
import io.lettuce.core.event.EventBus;
import io.lettuce.core.event.connection.ConnectedEvent;
import io.lettuce.core.event.connection.DisconnectedEvent;
import io.lettuce.core.event.connection.ReconnectFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hotels.molten.healthcheck.Health;
import com.hotels.molten.healthcheck.Status;
import com.hotels.molten.test.AssertSubscriber;

/**
 * Unit test for {@link LettuceEventBasedHealthIndicator}.
 */
@Slf4j
@Listeners(MockitoTestNGListener.class)
public class LettuceEventBasedHealthIndicatorTest {

    private static final InetSocketAddress LOCAL = new InetSocketAddress("local", 1111);
    private static final InetSocketAddress REMOTE_1 = new InetSocketAddress("remote1", 2222);
    private static final InetSocketAddress REMOTE_2 = new InetSocketAddress("remote2", 3333);

    @Mock
    private EventBus eventBus;
    private EmitterProcessor<Event> events;

    @BeforeMethod
    public void setUp() {
        events = EmitterProcessor.create();
        when(eventBus.get()).thenReturn(events);
    }

    @Test
    public void should_emit_initial_down() {
        StepVerifier.create(indicator().health().take(1))
            .expectNextMatches(health -> health.status() == Status.DOWN)
            .verifyComplete();
    }

    @Test
    public void disconnected_should_emit_down() {
        events.onNext(new DisconnectedEvent(LOCAL, REMOTE_1));
        StepVerifier.create(indicator().health().take(1))
            .expectNextMatches(health -> health.status() == Status.DOWN)
            .verifyComplete();
    }

    @Test
    public void reconnect_failed_should_emit_down() {
        events.onNext(new ReconnectFailedEvent(LOCAL, REMOTE_1, new IllegalStateException(), 2));
        StepVerifier.create(indicator().health().take(1))
            .expectNextMatches(health -> health.status() == Status.DOWN)
            .verifyComplete();
    }

    @Test
    public void should_get_last_emitted_health() {
        Flux<Health> healths = indicator().health();
        events.onNext(new DisconnectedEvent(LOCAL, REMOTE_1));
        var testSubscriber1 = AssertSubscriber.<Health>create();
        healths.subscribe(testSubscriber1);
        var testSubscriber2 = AssertSubscriber.<Health>create();
        events.onNext(new ConnectedEvent(LOCAL, REMOTE_1));
        healths.subscribe(testSubscriber2);

        testSubscriber1.assertValues(values -> assertThat(values)
            .extracting(Health::status)
            .contains(Status.DOWN, Status.UP));
        testSubscriber2.assertValues(values -> assertThat(values)
            .extracting(Health::status)
            .contains(Status.UP));
    }

    @Test
    public void reconnection_should_emit_up() {
        StepVerifier.create(indicator().health().take(4))
            .expectNextMatches(health -> health.status() == Status.DOWN)
            .then(() -> events.onNext(new ConnectedEvent(LOCAL, REMOTE_1)))
            .expectNextMatches(Health::healthy)
            .then(() -> events.onNext(new DisconnectedEvent(LOCAL, REMOTE_1)))
            .expectNextMatches(health -> health.status() == Status.DOWN)
            .then(() -> events.onNext(new ConnectedEvent(LOCAL, REMOTE_1)))
            .expectNextMatches(Health::healthy)
            .verifyComplete();
    }

    @Test
    public void should_distinguish_different_remotes() {
        StepVerifier.create(indicator().health().take(4))
            .expectNextMatches(health -> health.status() == Status.DOWN)
            .then(() -> events.onNext(new ConnectedEvent(LOCAL, REMOTE_1)))
            .expectNextMatches(Health::healthy)
            .then(() -> events.onNext(new ConnectedEvent(LOCAL, REMOTE_2)))
            .then(() -> events.onNext(new DisconnectedEvent(LOCAL, REMOTE_1)))
            .expectNextMatches(health -> health.status() == Status.DOWN)
            .then(() -> events.onNext(new ConnectedEvent(LOCAL, REMOTE_1)))
            .expectNextMatches(Health::healthy)
            .verifyComplete();
    }

    private LettuceEventBasedHealthIndicator indicator() {
        return new LettuceEventBasedHealthIndicator(eventBus, "test");
    }
}
