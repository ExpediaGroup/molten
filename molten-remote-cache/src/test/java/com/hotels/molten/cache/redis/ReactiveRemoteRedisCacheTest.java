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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import io.lettuce.core.cluster.api.reactive.RedisAdvancedClusterReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;
import reactor.util.retry.Retry;

import com.hotels.molten.cache.CachedValue;
import com.hotels.molten.cache.NamedCacheKey;
import com.hotels.molten.core.mdc.MoltenMDC;
import com.hotels.molten.trace.test.AbstractTracingTest;

/**
 * Unit test for {@link ReactiveRemoteRedisCache}.
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
public class ReactiveRemoteRedisCacheTest extends AbstractTracingTest {
    private static final String KEY = "key";
    private static final String CACHE_NAME = "cacheName";
    private static final String VALUE = "value";
    private static final long TTL_IN_SECONDS = 3L;
    private static final Duration TTL = Duration.ofSeconds(TTL_IN_SECONDS);
    private ReactiveRemoteRedisCache<String, String> redisCache;
    @Mock
    private RedisAdvancedClusterReactiveCommands<Object, Object> reactiveCommands;
    @Mock
    private RetryingRedisConnectionProvider redisConnectionProvider;
    private VirtualTimeScheduler scheduler;

    @BeforeEach
    public void initContext() {
        MoltenMDC.initialize();
        scheduler = VirtualTimeScheduler.create();
        VirtualTimeScheduler.set(scheduler);
    }

    @AfterEach
    public void tearDown() {
        VirtualTimeScheduler.reset();
        MoltenMDC.uninitialize();
    }

    @Test
    public void should_use_reactive_get_to_fetch_data() {
        when(redisConnectionProvider.connect()).thenReturn(Mono.just(reactiveCommands));
        redisCache = new ReactiveRemoteRedisCache<>(redisConnectionProvider);
        when(reactiveCommands.get(new NamedCacheKey<>(CACHE_NAME, KEY))).thenReturn(Mono.just(VALUE));

        StepVerifier.create(redisCache.get(new NamedCacheKey<>(CACHE_NAME, KEY))).expectNext(CachedValue.just(VALUE)).verifyComplete();
    }

    @Test
    public void should_use_reactive_put_to_store_data() {
        when(redisConnectionProvider.connect()).thenReturn(Mono.just(reactiveCommands));
        redisCache = new ReactiveRemoteRedisCache<>(redisConnectionProvider);
        when(reactiveCommands.setex(new NamedCacheKey<>(CACHE_NAME, KEY), TTL_IN_SECONDS, VALUE)).thenReturn(Mono.just("ok"));

        StepVerifier.create(redisCache.put(new NamedCacheKey<>(CACHE_NAME, KEY), CachedValue.of(VALUE, TTL))).verifyComplete();
    }

    @Test
    public void should_return_error_for_get_while_there_is_no_connection_to_redis() {
        when(redisConnectionProvider.connect()).thenReturn(Mono.empty());
        redisCache = new ReactiveRemoteRedisCache<>(redisConnectionProvider);

        StepVerifier.create(redisCache.get(new NamedCacheKey<>(CACHE_NAME, KEY))).expectError(IllegalStateException.class).verify();
        verifyNoMoreInteractions(reactiveCommands);
    }

    @Test
    public void should_return_error_for_put_while_there_is_no_connection_to_redis() {
        when(redisConnectionProvider.connect()).thenReturn(Mono.empty());
        redisCache = new ReactiveRemoteRedisCache<>(redisConnectionProvider);

        StepVerifier.create(redisCache.put(new NamedCacheKey<>(CACHE_NAME, KEY), CachedValue.of(VALUE, TTL))).expectError(IllegalStateException.class).verify();
        verifyNoMoreInteractions(reactiveCommands);
    }

    @Test
    public void should_return_error_for_get_while_there_is_no_connection_then_return_value_after_connection_is_established() {
        when(redisConnectionProvider.connect()).thenReturn(Mono.just(0).delayElement(Duration.ofSeconds(1), scheduler).map(e -> reactiveCommands));
        redisCache = new ReactiveRemoteRedisCache<>(redisConnectionProvider);

        StepVerifier.create(redisCache.get(new NamedCacheKey<>(CACHE_NAME, KEY))).expectError(IllegalStateException.class).verify();
        verifyNoMoreInteractions(reactiveCommands);
        scheduler.advanceTimeBy(Duration.ofSeconds(1));
        when(reactiveCommands.get(new NamedCacheKey<>(CACHE_NAME, KEY))).thenReturn(Mono.just(VALUE));
        StepVerifier.create(redisCache.get(new NamedCacheKey<>(CACHE_NAME, KEY))).expectNext(CachedValue.just(VALUE)).verifyComplete();
        verify(reactiveCommands).get(new NamedCacheKey<>(CACHE_NAME, KEY));
    }

    @Test
    public void should_return_value_with_retry_once_connected() {
        Sinks.One<RedisAdvancedClusterReactiveCommands<Object, Object>> connection = Sinks.one();
        when(redisConnectionProvider.connect()).thenReturn(connection.asMono());
        when(reactiveCommands.get(new NamedCacheKey<>(CACHE_NAME, KEY))).thenReturn(Mono.just(VALUE));
        redisCache = new ReactiveRemoteRedisCache<>(redisConnectionProvider);
        // verify it fails initially
        StepVerifier.create(redisCache.get(new NamedCacheKey<>(CACHE_NAME, KEY)))
            .expectError(IllegalStateException.class)
            .verify();
        // verify it succeeds after connection is established
        StepVerifier.create(redisCache.get(new NamedCacheKey<>(CACHE_NAME, KEY))
            .retryWhen(Retry.max(1).doBeforeRetry(x -> {
                connection.tryEmitValue(reactiveCommands); // let's assume the connection is established
            })))
            .expectNext(CachedValue.just(VALUE))
            .verifyComplete();
    }

    @Test
    public void should_succeed_with_retry_once_connected() {
        Sinks.One<RedisAdvancedClusterReactiveCommands<Object, Object>> connection = Sinks.one();
        when(redisConnectionProvider.connect()).thenReturn(connection.asMono());
        when(reactiveCommands.setex(new NamedCacheKey<>(CACHE_NAME, KEY), TTL_IN_SECONDS, VALUE)).thenReturn(Mono.just("ok"));
        redisCache = new ReactiveRemoteRedisCache<>(redisConnectionProvider);
        StepVerifier.create(redisCache.put(new NamedCacheKey<>(CACHE_NAME, KEY), CachedValue.of(VALUE, TTL))
            .retryWhen(Retry.max(1).doBeforeRetry(x -> {
                connection.tryEmitValue(reactiveCommands); // let's assume the connection is established
            })))
            .verifyComplete();
    }

    @Test
    public void should_propagate_MDC() {
        VirtualTimeScheduler.reset();
        when(redisConnectionProvider.connect()).thenReturn(Mono.just(reactiveCommands));
        redisCache = new ReactiveRemoteRedisCache<>(redisConnectionProvider);
        when(reactiveCommands.get(new NamedCacheKey<>(CACHE_NAME, KEY)))
            .thenReturn(Mono.<Object>just(VALUE).subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.parallel()));

        MDC.put("a", "b");
        StepVerifier.create(redisCache.get(new NamedCacheKey<>(CACHE_NAME, KEY)).map(x -> MDC.get("a"))).thenAwait().expectNext("b").verifyComplete();
    }

    @Test
    public void should_propagate_MDC_for_cache_miss() {
        VirtualTimeScheduler.reset();
        when(redisConnectionProvider.connect()).thenReturn(Mono.just(reactiveCommands));
        redisCache = new ReactiveRemoteRedisCache<>(redisConnectionProvider);
        when(reactiveCommands.get(new NamedCacheKey<>(CACHE_NAME, KEY))).thenReturn(Mono.empty().subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.parallel()));

        MDC.put("a", "b");
        StepVerifier.create(redisCache.get(new NamedCacheKey<>(CACHE_NAME, KEY)).doOnSuccess(x -> assertThat(MDC.get("a")).isEqualTo("b"))).thenAwait().verifyComplete();
    }

    @Test
    public void should_propagate_MDC_for_put() {
        VirtualTimeScheduler.reset();
        when(redisConnectionProvider.connect()).thenReturn(Mono.just(reactiveCommands));
        redisCache = new ReactiveRemoteRedisCache<>(redisConnectionProvider);
        when(reactiveCommands.setex(new NamedCacheKey<>(CACHE_NAME, KEY), TTL_IN_SECONDS, VALUE)).thenReturn(
            Mono.just("ok").subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.parallel()));

        MDC.put("a", "b");
        StepVerifier.create(redisCache.put(new NamedCacheKey<>(CACHE_NAME, KEY), CachedValue.of(VALUE, TTL))
            .doOnSuccess(x -> assertThat(MDC.get("a")).isEqualTo("b")))
            .thenAwait().verifyComplete();
    }

    @Test
    public void should_propagate_trace_context() {
        VirtualTimeScheduler.reset();
        when(redisConnectionProvider.connect()).thenReturn(Mono.just(reactiveCommands));
        redisCache = new ReactiveRemoteRedisCache<>(redisConnectionProvider);
        when(reactiveCommands.get(new NamedCacheKey<>(CACHE_NAME, KEY)))
            .thenReturn(Mono.<Object>just(VALUE).subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.parallel()));

        assertTraceContextIsPropagated(redisCache.get(new NamedCacheKey<>(CACHE_NAME, KEY)));
    }
}
