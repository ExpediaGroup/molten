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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.time.Duration;
import java.util.function.Supplier;

import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.reactive.RedisAdvancedClusterReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

/**
 * Unit test for {@link RetryingRedisConnectionProvider}.
 */
@Slf4j
public class RetryingRedisConnectionProviderTest {

    @Mock
    private Supplier<StatefulRedisClusterConnection<Object, Object>> connectionSupplier;
    @Mock
    private StatefulRedisClusterConnection<Object, Object> connection;
    @Mock
    private RedisAdvancedClusterReactiveCommands<Object, Object> reactiveCommands;
    private RetryingRedisConnectionProvider connectionProvider;
    private VirtualTimeScheduler scheduler;

    @BeforeMethod
    public void initContext() {
        initMocks(this);
        scheduler = VirtualTimeScheduler.create();
        VirtualTimeScheduler.set(scheduler);
        connectionProvider = new RetryingRedisConnectionProvider(connectionSupplier);
        when(reactiveCommands.clusterNodes()).thenReturn(Mono.empty());
    }

    @AfterMethod
    public void tearDown() {
        VirtualTimeScheduler.reset();
    }

    @Test
    public void should_retry_connecting_to_redis() {
        when(connectionSupplier.get())
            .thenThrow(new IllegalStateException("Error connecting to Redis!"))
            .thenThrow(new IllegalStateException("Error connecting to Redis!"))
            .thenThrow(new IllegalStateException("Error connecting to Redis!"))
            .thenReturn(connection);
        when(connection.reactive()).thenReturn(reactiveCommands);

        StepVerifier.withVirtualTime(() -> connectionProvider.connect())
            .expectSubscription()
            .thenAwait(Duration.ofMillis(500)) // retries are ~25ms, ~50ms, ~100ms
            .expectNext(reactiveCommands)
            .verifyComplete();

        verify(connectionSupplier, times(4)).get();
    }
}
