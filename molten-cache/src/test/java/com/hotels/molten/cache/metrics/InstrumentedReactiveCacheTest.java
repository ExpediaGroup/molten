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

package com.hotels.molten.cache.metrics;

import static com.hotels.molten.core.metrics.MetricsSupport.GRAPHITE_ID;
import static com.hotels.molten.core.metrics.MetricsSupport.name;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.cache.ReactiveMapCache;
import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Unit test for {@link InstrumentedReactiveCache}.
 */
public class InstrumentedReactiveCacheTest {
    private static final String QUALIFIER = "qualifier";
    private static final String CACHE_NAME = "cache_name";
    private static final String REACTIVE_CACHE = "reactive-cache";
    private ReactiveCache<String, String> cache;
    private MeterRegistry meterRegistry;

    @BeforeMethod
    public void initContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    }

    @AfterMethod
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
    }

    @Test
    public void should_report_get_and_hit_an_hit_ratio() {
        // Given
        cache = new InstrumentedReactiveCache<>(new ReactiveMapCache<>(new HashMap<>()), meterRegistry, CACHE_NAME, QUALIFIER);
        // When
        StepVerifier.create(cache.put("key1", "value1")).verifyComplete();
        StepVerifier.create(cache.get("key1")).expectNext("value1").verifyComplete();
        StepVerifier.create(cache.get("key2")).verifyComplete();
        // Then
        assertThat(meterRegistry.get(name(REACTIVE_CACHE, CACHE_NAME, "put", QUALIFIER, "success")).timer().count()).isEqualTo(1L);
        assertThat(meterRegistry.get(name(REACTIVE_CACHE, CACHE_NAME, "get", QUALIFIER, "success")).timer().count()).isEqualTo(2L);
        assertThat(meterRegistry.get(name(REACTIVE_CACHE, CACHE_NAME, "hit-ratio", QUALIFIER)).gauge().value()).isEqualTo(0.5D);
    }

    @Test
    public void should_report_dimensional_metrics_when_enabled() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);

        cache = new InstrumentedReactiveCache<>(new ReactiveMapCache<>(new HashMap<>()), meterRegistry, CACHE_NAME, QUALIFIER);
        // When
        StepVerifier.create(cache.put("key1", "value1")).verifyComplete();
        StepVerifier.create(cache.get("key1")).expectNext("value1").verifyComplete();
        StepVerifier.create(cache.get("key2")).verifyComplete();
        // Then
        assertThat(meterRegistry.get("cache_requests")
            .tag("name", CACHE_NAME)
            .tag("type", QUALIFIER)
            .tag("status", "success")
            .tag("operation", "put")
            .tag(GRAPHITE_ID, name(REACTIVE_CACHE, CACHE_NAME, "put", QUALIFIER, "success"))
            .timer().count()).isEqualTo(1L);
        assertThat(meterRegistry.get("cache_requests")
            .tag("name", CACHE_NAME)
            .tag("type", QUALIFIER)
            .tag("status", "success")
            .tag("operation", "get")
            .tag(GRAPHITE_ID, name(REACTIVE_CACHE, CACHE_NAME, "get", QUALIFIER, "success"))
            .timer().count()).isEqualTo(2L);
        assertThat(meterRegistry.get("cache_hit_ratio")
            .tag("name", CACHE_NAME)
            .tag("type", QUALIFIER)
            .tag(GRAPHITE_ID, name(REACTIVE_CACHE, CACHE_NAME, "hit-ratio", QUALIFIER))
            .gauge().value()).isEqualTo(0.5D);
    }

    @Test
    public void should_count_error_as_a_miss() {
        //Given
        ReactiveCache<String, String> mockCache = mock(ReactiveCache.class);
        cache = new InstrumentedReactiveCache<>(mockCache, meterRegistry, CACHE_NAME, QUALIFIER);
        // When
        when(mockCache.get("key1")).thenReturn(Mono.just("value1"));
        when(mockCache.get("key2")).thenReturn(Mono.error(new IllegalStateException()));
        StepVerifier.create(cache.get("key1")).expectNext("value1").verifyComplete();
        StepVerifier.create(cache.get("key2")).expectError(IllegalStateException.class).verify();
        // Then
        assertThat(meterRegistry.get(name(REACTIVE_CACHE, CACHE_NAME, "hit-ratio", QUALIFIER)).gauge().value()).isEqualTo(0.5D);
    }
}
