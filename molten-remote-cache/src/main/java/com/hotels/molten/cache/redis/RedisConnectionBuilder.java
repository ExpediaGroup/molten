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

import static com.google.common.base.Preconditions.checkArgument;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.event.DefaultEventPublisherOptions;
import io.lettuce.core.metrics.DefaultCommandLatencyCollector;
import io.lettuce.core.metrics.DefaultCommandLatencyCollectorOptions;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hotels.molten.cache.redis.metrics.CommandLatencyMetricsCollector;
import com.hotels.molten.healthcheck.HealthIndicatorWatcher;

/**
 * Builder to create a connection for a Redis cluster.
 */
public class RedisConnectionBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(RedisConnectionBuilder.class);
    private List<RedisURI> redisSeedUris;
    private Integer ioThreadPoolSize;
    private Integer computationThreadPoolSize;
    private ClusterClientOptions clusterClientOptions = defaultClusterClientOptions(defaultClusterTopologyRefreshOptions());
    private String metricsQualifier;
    private MeterRegistry meterRegistry;
    private RedisCodec<Object, Object> codec;
    private HealthIndicatorWatcher healthIndicatorWatcher;

    /**
     * Createst the Redis connection based on the settings.
     *
     * @return the Redis connection
     */
    public StatefulRedisClusterConnection<Object, Object> createConnection() {
        requireNonNull(redisSeedUris);
        requireNonNull(clusterClientOptions);
        requireNonNull(codec);
        LOG.info("Creating Redis connection to {}", redisSeedUris);
        RedisClusterClient cluster = RedisClusterClient.create(buildClientResources(), redisSeedUris);
        cluster.setOptions(clusterClientOptions);
        if (healthIndicatorWatcher != null) {
            healthIndicatorWatcher.watch(new LettuceEventBasedHealthIndicator(cluster.getResources().eventBus(), metricsQualifier));
        }
        return cluster.connect(codec);
    }

    public static RedisConnectionBuilder builder() {
        return new RedisConnectionBuilder();
    }

    private DefaultClientResources buildClientResources() {
        DefaultClientResources.Builder builder = DefaultClientResources.builder();
        if (ioThreadPoolSize != null) {
            builder.ioThreadPoolSize(ioThreadPoolSize);
        }
        if (computationThreadPoolSize != null) {
            builder.computationThreadPoolSize(computationThreadPoolSize);
        }
        if (meterRegistry != null) {
            builder.commandLatencyCollector(new CommandLatencyMetricsCollector(meterRegistry, metricsQualifier));
        } else {
            builder.commandLatencyCollector(DefaultCommandLatencyCollector.disabled());
            builder.commandLatencyCollectorOptions(DefaultCommandLatencyCollectorOptions.disabled());
            builder.commandLatencyPublisherOptions(DefaultEventPublisherOptions.disabled());
        }
        return builder.build();
    }

    /**
     * Creates a default {@link ClusterClientOptions}.
     *
     * @param clusterTopologyRefreshOptions the {@link ClusterTopologyRefreshOptions} to use
     * @return the client options
     */
    public static ClusterClientOptions defaultClusterClientOptions(ClusterTopologyRefreshOptions clusterTopologyRefreshOptions) {
        return ClusterClientOptions.builder()
            .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(2)).tcpNoDelay(true).build())
            .topologyRefreshOptions(clusterTopologyRefreshOptions)
            .validateClusterNodeMembership(false)
            .build();
    }

    /**
     * Creates a default {@link ClusterTopologyRefreshOptions}.
     *
     * @return the cluster topology options
     */
    public static ClusterTopologyRefreshOptions defaultClusterTopologyRefreshOptions() {
        return ClusterTopologyRefreshOptions.builder()
            .enablePeriodicRefresh(Duration.of(15, MINUTES))
            .closeStaleConnections(true)
            .enableAllAdaptiveRefreshTriggers()
            .adaptiveRefreshTriggersTimeout(Duration.of(30, SECONDS))
            .refreshTriggersReconnectAttempts(5)
            .dynamicRefreshSources(false)
            .build();
    }

    /**
     * Sets the seed nodes in a Redis URI format.
     *
     * @param uris the redis cluster URIs
     * @return this builder instance
     */
    public RedisConnectionBuilder withRedisSeedUris(List<RedisURI> uris) {
        redisSeedUris = List.copyOf(uris);
        return this;
    }

    /**
     * Sets the seed nodes in a comma separated list of Redis URIs in the following format.
     * {@code redis://[password@]host[:port][/database][?[timeout=timeout[d|h|m|s|ms|us|ns]][&database=database]]}
     *
     * @param uris Redis Cluster seed URIs
     * @return this builder instance
     */
    public RedisConnectionBuilder withRedisSeedUris(String uris) {
        redisSeedUris = Pattern.compile(",\\s*").splitAsStream(uris).map(RedisURI::create).collect(toList());
        return this;
    }

    /**
     * The number of threads in the I/O thread pools.
     * Defaults to the number of available processors with a minimum of 3 threads.
     *
     * @param ioThreadPoolSize I/O thread pool size
     * @return this builder instance
     */
    public RedisConnectionBuilder withIoThreadPoolSize(int ioThreadPoolSize) {
        checkArgument(ioThreadPoolSize > 0);
        this.ioThreadPoolSize = ioThreadPoolSize;
        return this;
    }

    /**
     * The number of threads in the computation thread pool.
     * Defaults to the number of available processors with a minimum of 3 threads.
     *
     * @param computationThreadPoolSize computation thread pool size
     * @return this builder instance
     */
    public RedisConnectionBuilder withComputationThreadPoolSize(int computationThreadPoolSize) {
        checkArgument(computationThreadPoolSize > 0);
        this.computationThreadPoolSize = computationThreadPoolSize;
        return this;
    }

    /**
     * Sets the cluster client options.
     *
     * @param clusterClientOptions the cluster client options
     * @return this builder instance
     */
    public RedisConnectionBuilder withClusterClientOptions(ClusterClientOptions clusterClientOptions) {
        this.clusterClientOptions = requireNonNull(clusterClientOptions);
        return this;
    }

    /**
     * Sets the meter registry to register metrics with.
     *
     * @param meterRegistry   the meter registry to register metrics with
     * @param metricsQualifier the metric qualifier to prefix metrics with
     * @return this builder instance
     */
    public RedisConnectionBuilder withMeterRegistry(MeterRegistry meterRegistry, String metricsQualifier) {
        this.meterRegistry = requireNonNull(meterRegistry);
        this.metricsQualifier = requireNonNull(metricsQualifier);
        return this;
    }

    /**
     * Sets the Redis codec to serialize/deserialize objects.
     *
     * @param codec the Redis codec
     * @return this builder instance
     */
    public RedisConnectionBuilder withCodec(RedisCodec<Object, Object> codec) {
        this.codec = requireNonNull(codec);
        return this;
    }

    /**
     * Sets the {@code healthIndicatorWatcher} to be used for watching the health of the this connection.
     *
     * @param healthIndicatorWatcher the {@code healthIndicatorWatcher} to set
     * @return a reference to this Builder
     */
    public RedisConnectionBuilder withHealthIndicatorWatcher(HealthIndicatorWatcher healthIndicatorWatcher) {
        this.healthIndicatorWatcher = requireNonNull(healthIndicatorWatcher);
        return this;
    }
}
