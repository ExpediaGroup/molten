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

package com.hotels.molten.trace;

import static com.hotels.molten.trace.test.SpanMatcher.rootSpanWithName;
import static com.hotels.molten.trace.test.SpanMatcher.spanWithName;
import static com.hotels.molten.trace.test.TracingTestSupport.capturedSpans;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import brave.Tracing;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import zipkin2.Span;

import com.hotels.molten.core.MoltenCore;
import com.hotels.molten.core.mdc.MoltenMDC;
import com.hotels.molten.trace.Tracer.TraceSpan;
import com.hotels.molten.trace.test.TracingTestSupport;

/**
 * Unit test for {@link MoltenTrace}.
 */
@Slf4j
public class MoltenTraceTest {

    @BeforeAll
    static void init() {
        Hooks.enableContextLossTracking();
    }

    @AfterAll
    static void clear() {
        Hooks.disableContextLossTracking();
    }

    @BeforeEach
    void initContext() {
        MoltenCore.initialize();
        TracingTestSupport.initialize(false);
        MoltenTrace.initialize();
    }

    @AfterEach
    void clearContext() {
        MoltenCore.uninitialize();
        MoltenTrace.uninitialize();
        MoltenMDC.uninitialize();
        TracingTestSupport.resetCapturedSpans();
        Tracing current = Tracing.current();
        if (current != null) {
            current.currentTraceContext().maybeScope(null);
            current.setNoop(false);
        }
        TracingTestSupport.cleanUp();
    }

    static Stream<Boolean> onEachOperatorEnabled() {
        return Stream.of(true, false);
    }

    @ParameterizedTest
    @MethodSource("onEachOperatorEnabled")
    void should_support_nesting(boolean onEachOperatorEnabled) {
        MoltenTrace.initialize(onEachOperatorEnabled);
        try (TraceSpan outer = Tracer.span("outer").start()) {
            StepVerifier.create(
                Mono.just("data")
                    .map(d -> Tracer.span("inner").wrap(() -> d + ".altered"))
            )
                .expectNext("data.altered")
                .verifyComplete();
        }
        assertThat(capturedSpans(), contains(spanWithName("inner"), rootSpanWithName("outer")));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("onEachOperatorEnabled")
    void should_support_fully_reactive_nesting(boolean onEachOperatorEnabled) {
        MoltenTrace.initialize(onEachOperatorEnabled);
        CountDownLatch latch = new CountDownLatch(1);
        StepVerifier.create(
            Mono.just("data")
                .doFinally(s -> latch.countDown())
                .transform(TracingTransformer.span("outer").forMono())
                .publishOn(Schedulers.boundedElastic())
                .transform(TracingTransformer.span("mid").forMono())
                .publishOn(Schedulers.parallel())
                .transform(TracingTransformer.span("inner").forMono())
                .subscribeOn(Schedulers.single())
        )
            .expectNext("data")
            .verifyComplete();
        latch.await();
        Assertions.assertThat(capturedSpans())
            .anySatisfy(span -> {
                Assertions.assertThat(span).extracting(Span::parentId).isNull();
                Assertions.assertThat(span).extracting(Span::name).isEqualTo("outer");
            });
        var outerSpan = capturedSpans().stream().filter(span -> "outer".equals(span.name())).findFirst().orElseThrow();
        Assertions.assertThat(capturedSpans())
            .anySatisfy(span -> {
                Assertions.assertThat(span).extracting(Span::parentId).isEqualTo(outerSpan.id());
                Assertions.assertThat(span).extracting(Span::name).isEqualTo("mid");
            });
        var midSpan = capturedSpans().stream().filter(span -> "mid".equals(span.name())).findFirst().orElseThrow();
        Assertions.assertThat(capturedSpans())
            .anySatisfy(span -> {
                Assertions.assertThat(span).extracting(Span::parentId).isEqualTo(midSpan.id());
                Assertions.assertThat(span).extracting(Span::name).isEqualTo("inner");
            });
    }

    @ParameterizedTest
    @MethodSource("onEachOperatorEnabled")
    void should_propagate_when_switching_schedulers(boolean onEachOperatorEnabled) {
        MoltenTrace.initialize(onEachOperatorEnabled);
        try (TraceSpan outer = Tracer.span("outer").start()) {
            StepVerifier.create(
                Mono.just("data")
                    .publishOn(Schedulers.parallel())
                    .map(d -> Tracer.span("inner").wrap(() -> d + ".altered"))
            )
                .thenAwait()
                .expectNext("data.altered")
                .verifyComplete();
        }
        assertThat(capturedSpans(), contains(spanWithName("inner"), rootSpanWithName("outer")));
    }

    @ParameterizedTest
    @MethodSource("onEachOperatorEnabled")
    void should_propagate_when_switching_schedulers_for_subscribe(boolean onEachOperatorEnabled) {
        MoltenTrace.initialize(onEachOperatorEnabled);
        try (Tracer.TraceSpan outer = Tracer.span("outer").start()) {
            StepVerifier.create(
                Mono.just("data")
                    .subscribeOn(Schedulers.parallel())
                    .map(d -> Tracer.span("inner").wrap(() -> d + ".altered"))
            )
                .thenAwait()
                .expectNext("data.altered")
                .verifyComplete();
        }
        assertThat(capturedSpans(), contains(spanWithName("inner"), rootSpanWithName("outer")));
    }

    @ParameterizedTest
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @MethodSource("onEachOperatorEnabled")
    void should_propagate_trace_context_in_indirect_flow(boolean onEachOperatorEnabled) {
        MoltenTrace.initialize(onEachOperatorEnabled);
        try (Tracer.TraceSpan outer = Tracer.span("outer").start()) {
            var outerSpan = Optional.ofNullable(Tracing.currentTracer()).map(brave.Tracer::currentSpan).orElse(null);
            LOG.debug("outer span={}", outerSpan);
            StepVerifier.create(
                Mono.create(sink -> {
                    new Thread(() -> {
                        var currentSpan = Optional.ofNullable(Tracing.currentTracer()).map(brave.Tracer::currentSpan).orElse(null);
                        assertThat("There should be no trace context on this thread", currentSpan, nullValue());
                        sink.success("data");
                    }).start();
                })
                    .transform(MoltenCore.propagateContext())
                    .publishOn(Schedulers.parallel())
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(d -> Tracer.span("inner").wrap(() -> {
                        var innerSpan = Optional.ofNullable(Tracing.currentTracer()).map(brave.Tracer::currentSpan).orElse(null);
                        LOG.debug("inner span={}", innerSpan);
                        return d + ".altered";
                    }))
            )
                .thenAwait(Duration.ofSeconds(3))
                .expectNext("data.altered")
                .verifyComplete();
        }
        assertThat(capturedSpans(), contains(spanWithName("inner"), rootSpanWithName("outer")));
    }

    @Test
    void should_work_with_disabled_tracing() {
        MoltenTrace.uninitialize();
        TracingTestSupport.cleanUp();
        StepVerifier.create(
            Mono.just("data")
                .subscribeOn(Schedulers.parallel())
                .transform(MoltenTrace.propagate())
                .map(d -> d + ".altered")
        )
            .expectNext("data.altered")
            .verifyComplete();
    }
}
