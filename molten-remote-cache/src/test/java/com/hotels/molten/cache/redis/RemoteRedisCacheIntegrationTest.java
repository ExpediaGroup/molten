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

import static com.hotels.molten.cache.redis.RedisConnectionBuilder.defaultClusterClientOptions;
import static com.hotels.molten.cache.redis.RedisConnectionBuilder.defaultClusterTopologyRefreshOptions;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Capability;
import com.google.common.collect.ImmutableList;
import de.javakaffee.kryoserializers.guava.ImmutableListSerializer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.lettuce.core.codec.RedisCodec;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

import com.hotels.molten.cache.CachedValue;
import com.hotels.molten.cache.NamedCacheKey;
import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.cache.redis.codec.FlatStringKeyCodec;
import com.hotels.molten.cache.redis.codec.KryoRedisCodec;
import com.hotels.molten.cache.resilience.FailSafeMode;
import com.hotels.molten.cache.resilience.ResilientReactiveCacheBuilder;

/**
 * Integration test for resilient redis cache.
 */
@Slf4j
@Testcontainers
public class RemoteRedisCacheIntegrationTest {
    @Container
    private static final GenericContainer<?> REDIS_CLUSTER = createRedisCluster();

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private static GenericContainer<?> createRedisCluster() {
        Consumer<CreateContainerCmd> containerCmdModifier = cmd -> cmd.getHostConfig().withCapAdd(Capability.NET_ADMIN);
        return new GenericContainer<>(DockerImageName.parse("redis:5.0.3-alpine"))
            .withCreateContainerCmdModifier(containerCmdModifier)
            .withCopyFileToContainer(MountableFile.forClasspathResource("redis/redis.conf"), "/data/redis.conf")
            .withCopyFileToContainer(MountableFile.forClasspathResource("redis/nodes.conf"), "/data/nodes.conf")
            .withCommand("redis-server", "/data/redis.conf")
            .withExposedPorts(6379)
            .waitingFor(new RedisPingCheck());
    }

    @Test
    public void should_build_reactive_cache_over_redis() {
        // Given
        ReactiveCache<ComplexKey, ComplexValue> remoteCache = reactiveCache();
        ComplexKey key = new ComplexKey("some", ImmutableList.of(1, 2, 3));
        ComplexValue value = new ComplexValue("value", ImmutableList.of(3, 2, 1));
        // When
        StepVerifier.create(remoteCache.put(key, value)).thenAwait().verifyComplete();
        // Then
        StepVerifier.create(remoteCache.get(key)).thenAwait().expectNext(value).verifyComplete();
    }

    private ReactiveCache<ComplexKey, ComplexValue> reactiveCache() {
        return ResilientReactiveCacheBuilder.<ComplexKey, ComplexValue>builder()
            .over(sharedRemoteCache())
            .withCacheName("myCache")
            .withTimeout(Duration.ofMillis(1000))
            .withRetry(Retry.fixedDelay(10, Duration.ofSeconds(1)))
            .withFailsafe(FailSafeMode.VERBOSE)
            .withTTL(Duration.ofSeconds(500))
            .withCircuitBreakerConfig(CircuitBreakerConfig.custom()
                .failureRateThreshold(75)
                .waitDurationInOpenState(Duration.ofMillis(2000))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindow(20, 20, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .build())
            .withMeterRegistry(meterRegistry)
            .createCache();
    }

    private <K, V> ReactiveCache<NamedCacheKey<K>, CachedValue<V>> sharedRemoteCache() {
        return ResilientSharedReactiveRedisCacheBuilder.<K, V>builder()
            .withRedisConnection(() -> redisConnectionBuilder().createConnection())
            .withSharedCacheName("sharedRemoteCache")
            .withMaxConcurrency(2)
            .withMeterRegistry(meterRegistry)
            .createCache();
    }

    private RedisConnectionBuilder redisConnectionBuilder() {
        LOG.info("Creating redis client for {}:{}", REDIS_CLUSTER.getHost(), REDIS_CLUSTER.getFirstMappedPort());
        return RedisConnectionBuilder.builder()
            .withRedisSeedUris("redis://" + REDIS_CLUSTER.getHost() + ":" + REDIS_CLUSTER.getFirstMappedPort() + "?timeout=10000")
            .withClusterClientOptions(defaultClusterClientOptions(defaultClusterTopologyRefreshOptions()))
            .withCodec(createCodec())
            .withMeterRegistry(meterRegistry, "remote-cache.sharedRemoteCache.redis");
    }

    private RedisCodec<Object, Object> createCodec() {
        return RedisCodec.of(new FlatStringKeyCodec<>(), new KryoRedisCodec<>(kryoPool()));
    }

    private KryoPool kryoPool() {
        return new KryoPool.Builder(() -> {
            Kryo kryo = new Kryo();
            kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
            ImmutableListSerializer.registerSerializers(kryo);
            return kryo;
        }).build();
    }

    @Value
    static class ComplexKey {
        private final String text;
        private final List<Integer> numbers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ComplexValue {
        private String text;
        private List<Integer> numbers;
    }
}
