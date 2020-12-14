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

import java.time.Duration;
import java.util.function.Supplier;

import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.reactive.RedisAdvancedClusterReactiveCommands;
import io.lettuce.core.cluster.models.partitions.ClusterPartitionParser;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import com.hotels.molten.cache.NamedCacheKey;

/**
 * Retrying Redis cluster connection provider.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class RetryingRedisConnectionProvider<K, V> {

    @NonNull
    private final Supplier<StatefulRedisClusterConnection<NamedCacheKey<K>, V>> connectionSupplier;

    /**
     * Retries connecting to Redis cluster until is succeeds.
     *
     * @return Redis Cluster API
     */
    Mono<RedisAdvancedClusterReactiveCommands<NamedCacheKey<K>, V>> connect() {
        //FIXME DefaultClientResources might need to be closed before retry
        return Mono.fromCallable(connectionSupplier::get)
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError(e -> LOG.warn("Error connecting to Redis, using NOOP remote cache implementation.", e))
            .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(25))
                .jitter(0.25D)
                .maxBackoff(Duration.ofSeconds(30))
                .doBeforeRetry(s -> LOG.warn("Retrying #{} connecting to Redis", s.totalRetries())))
            .map(StatefulRedisClusterConnection::reactive)
            .flatMap(api -> api.clusterNodes()
                .map(this::getInfo)
                .doOnNext(info -> LOG.info("Successfully connected to Redis cluster {}", info))
                .map(x -> api)
                .defaultIfEmpty(api));
    }

    private String getInfo(String clusterNodes) {
        return ClusterPartitionParser.parse(clusterNodes).toString();
    }
}
