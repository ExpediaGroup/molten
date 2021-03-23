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

import java.time.Duration;
import java.util.function.Supplier;

import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.reactive.RedisAdvancedClusterReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import com.hotels.molten.cache.NamedCacheKey;

/**
 * Unit test for {@link RetryingRedisConnectionProvider}.
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
public class RetryingRedisConnectionProviderTest {

    @Mock
    private Supplier<StatefulRedisClusterConnection<NamedCacheKey<Object>, Object>> connectionSupplier;
    @Mock
    private StatefulRedisClusterConnection<NamedCacheKey<Object>, Object> connection;
    @Mock
    private RedisAdvancedClusterReactiveCommands<NamedCacheKey<Object>, Object> reactiveCommands;
    @InjectMocks
    private RetryingRedisConnectionProvider<Object, Object> connectionProvider;

    @BeforeEach
    public void initContext() {
        when(reactiveCommands.clusterNodes()).thenReturn(Mono.empty());
    }

    @AfterEach
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
