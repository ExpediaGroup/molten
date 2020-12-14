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

import io.github.resilience4j.core.StopWatch;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class RedisPingCheck extends AbstractWaitStrategy {

    @Override
    protected void waitUntilReady() {
        StopWatch stopWatch = StopWatch.start();
        Boolean isReady = Flux.interval(Duration.ZERO, Duration.ofSeconds(1))
            .publishOn(Schedulers.boundedElastic())
            .take(10)
            .map(i -> isReadyRateLimited())
            .filter(ready -> ready)
            .defaultIfEmpty(false)
            .blockFirst();
        if (!isReady) {
            throw new ContainerLaunchException("Redis cluster was not ready after " + stopWatch.stop());
        }
    }

    private boolean isReadyRateLimited() {
        boolean result = false;
        try {
            result = getRateLimiter().getWhenReady(this::isPingReady);
        } catch (Exception e) {
            LOG.warn("Error executing rate limited ping", e);
        }
        return result;
    }

    private boolean isPingReady() {
        boolean isReady = false;
        try {
            isReady = waitStrategyTarget.execInContainer("redis-cli", "-p", "6379", "ping").getExitCode() == 0;
        } catch (Exception e) {
            LOG.warn("Error executing ping", e);
        }
        return isReady;
    }
}
