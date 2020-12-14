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

import static java.util.Objects.requireNonNull;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.lettuce.core.event.EventBus;
import io.lettuce.core.event.connection.ConnectedEvent;
import io.lettuce.core.event.connection.ConnectionActivatedEvent;
import io.lettuce.core.event.connection.ConnectionDeactivatedEvent;
import io.lettuce.core.event.connection.ConnectionEvent;
import io.lettuce.core.event.connection.DisconnectedEvent;
import io.lettuce.core.event.connection.ReconnectFailedEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.hotels.molten.healthcheck.CompositeHealthIndicator;
import com.hotels.molten.healthcheck.Health;
import com.hotels.molten.healthcheck.HealthIndicator;
import com.hotels.molten.healthcheck.Status;

/**
 * Reports health-check based on the connectionEvents emitted by the event bus.
 *
 * <p>The last emitted {@link Health} is replayed to each subscriber even for the new ones.</p>
 */
public final class LettuceEventBasedHealthIndicator implements HealthIndicator, CompositeHealthIndicator {
    private static final Map<Class<? extends ConnectionEvent>, Function<ConnectionEvent, Health>> EVENT_TO_HEALTH = Map.of(
        ConnectionDeactivatedEvent.class, event -> Health.down(String.format("Redis host [%s] connection is deactivated", event.remoteAddress())),
        DisconnectedEvent.class, event -> Health.down(String.format("Redis host [%s] is disconnected", event.remoteAddress())),
        ReconnectFailedEvent.class, event -> Health.down(String.format("Redis host [%s] reconnection failed", event.remoteAddress())),
        ConnectedEvent.class, event -> Health.builder(Status.UP).withMessage(String.format("Redis host [%s] is connected", event.remoteAddress())).build(),
        ConnectionActivatedEvent.class, event -> Health.builder(Status.UP).withMessage(String.format("Redis host [%s] connection is activated", event.remoteAddress())).build()
    );
    private final String name;
    private final CompositeHealthIndicator composite;
    private final Map<SocketAddress, HealthIndicator> hostIndicators = new ConcurrentHashMap<>();
    private final Flux<ConnectionEvent> connectionEvents;

    public LettuceEventBasedHealthIndicator(EventBus eventBus, String name) {
        this.name = requireNonNull(name);
        composite = HealthIndicator.composite(name + "_helper", Status.DOWN);
        connectionEvents = eventBus.get()
            .filter(ConnectionEvent.class::isInstance)
            .cast(ConnectionEvent.class);
        connectionEvents.subscribe(event -> hostIndicators.computeIfAbsent(event.remoteAddress(), remoteAddress -> {
            HealthIndicator hostIndicator = new HealthIndicator() {
                private final Flux<Health> remoteSpecificFlow = Flux.just(event)
                    .concatWith(connectionEvents)
                    .filter(e -> e.remoteAddress().equals(remoteAddress))
                    .flatMap(e -> Mono.justOrEmpty(EVENT_TO_HEALTH.get(e.getClass())).map(eh -> eh.apply(e)))
                    .replay(1)
                    .autoConnect();

                @Override
                public String name() {
                    return String.format("%s - redis node=%s", name, remoteAddress);
                }

                @Override
                public Flux<Health> health() {
                    return remoteSpecificFlow;
                }
            };
            composite.watch(hostIndicator);
            return hostIndicator;
        }));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Flux<Health> health() {
        return composite.health();
    }

    @Override
    public Collection<HealthIndicator> subIndicators() {
        return composite.subIndicators();
    }

    @Override
    public void watch(HealthIndicator indicator) {
        throw new UnsupportedOperationException("This composite health indicator exposes its own sub-components");
    }
}
