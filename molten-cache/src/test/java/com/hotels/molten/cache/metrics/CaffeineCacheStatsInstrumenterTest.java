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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Unit test for {@link CaffeineCacheStatsInstrumenter}.
 */
@ExtendWith(MockitoExtension.class)
public class CaffeineCacheStatsInstrumenterTest {
    private static final Logger LOG = (Logger) LoggerFactory.getLogger(CaffeineCacheStatsInstrumenter.class);
    @Mock
    private Appender<ILoggingEvent> appender;
    @Mock
    private Cache cache;
    private CaffeineCacheStatsInstrumenter instrumenter;
    private MeterRegistry meterRegistry;

    @BeforeEach
    public void initContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        instrumenter = new CaffeineCacheStatsInstrumenter(meterRegistry, "pre.fix");
        LOG.addAppender(appender);
    }

    @AfterEach
    public void clearContext() {
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        LOG.detachAppender(appender);
    }

    @Test
    public void should_record_hits() {
        instrumenter.recordHits(2);
        assertThat(meterRegistry.get("pre.fix.hit-count").counter().count()).isEqualTo(2D);
    }

    @Test
    public void should_record_misses() {
        instrumenter.recordMisses(2);
        assertThat(meterRegistry.get("pre.fix.miss-count").counter().count()).isEqualTo(2D);
    }

    @Test
    public void should_record_load_success_time() {
        instrumenter.recordLoadSuccess(60);
        instrumenter.recordLoadSuccess(50);
        Timer timer = meterRegistry.get("pre.fix.load-success").timer();
        assertThat(timer.count()).isEqualTo(2L);
        assertThat(timer.max(TimeUnit.NANOSECONDS)).isEqualTo(60D);
        assertThat(timer.mean(TimeUnit.NANOSECONDS)).isEqualTo(55D);
        assertThat(instrumenter.snapshot().totalLoadTime()).isEqualTo(110);
    }

    @Test
    public void should_record_load_failure_time() {
        instrumenter.recordLoadFailure(60);
        instrumenter.recordLoadFailure(50);
        Timer timer = meterRegistry.get("pre.fix.load-failure").timer();
        assertThat(timer.count()).isEqualTo(2L);
        assertThat(timer.max(TimeUnit.NANOSECONDS)).isEqualTo(60D);
        assertThat(timer.mean(TimeUnit.NANOSECONDS)).isEqualTo(55D);
        assertThat(instrumenter.snapshot().totalLoadTime()).isEqualTo(110);
    }

    @Test
    public void should_record_evictions() {
        instrumenter.recordEviction(2, RemovalCause.SIZE);
        assertThat(meterRegistry.get("pre.fix.eviction-count").counter().count()).isEqualTo(1D);
        assertThat(instrumenter.snapshot().evictionWeight()).isEqualTo(2L);
    }

    @Test
    public void should_register_cache_size() {
        instrumenter.registerCache(cache);
        when(cache.estimatedSize()).thenReturn(2L);
        assertThat(meterRegistry.get("pre.fix.size").gauge().value()).isEqualTo(2D);
    }

    @Test
    public void should_assemble_snapshot() {
        //this is not used though
        CacheStats snapshot = instrumenter.snapshot();
        assertThat(snapshot).isNotNull();
    }

    @Test
    public void should_register_dimensional_metrics() {
        // Given
        MoltenMetrics.setDimensionalMetricsEnabled(true);
        MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true);
        instrumenter = new CaffeineCacheStatsInstrumenter(meterRegistry, "cache-name");
        // When
        instrumenter.recordHits(1);
        // Then
        assertThat(meterRegistry.get("cache_hit_count").tag("name", "cache-name").tag(GRAPHITE_ID, "cache-name.hit-count").counter().count()).isEqualTo(1);
    }

    @Test
    void should_not_log_registered_instrumenter() {
        instrumenter.registerCache(cache);
        instrumenter.recordMisses(2);
        verifyNoInteractions(appender);

    }

    @Test
    void should_log_unregistered_instrumenter_once() {
        instrumenter.recordMisses(2);
        var captor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(appender).doAppend(captor.capture());
        assertThat(captor.getValue()).isNotNull();
        assertThat(captor.getValue().getLevel()).isEqualTo(Level.WARN);
        assertThat(captor.getValue().getFormattedMessage()).isEqualTo("Unregistered cache found with metricsQualifier=pre.fix. Estimated size metrics won't be available!");
        instrumenter.recordHits(1);
        instrumenter.recordMisses(3);
        verifyNoMoreInteractions(appender);
    }
}
