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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.function.Supplier;

import brave.Tracing;
import brave.propagation.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import com.hotels.molten.trace.Tracer.CheckedRunnable;
import com.hotels.molten.trace.Tracer.CheckedSupplier;
import com.hotels.molten.trace.Tracer.CheckedSupplier2;
import com.hotels.molten.trace.Tracer.TraceSpan;
import com.hotels.molten.trace.test.AbstractTracingTest;
import com.hotels.molten.trace.test.SpanMatcher;

/**
 * Unit test for {@link Tracer}.
 */
@Slf4j
public class TracerTest extends AbstractTracingTest {

    @Test
    public void should_wrap_call_to_span() {
        try (TraceSpan s = Tracer.span("trace").start()) {
            LOG.info("some task");
        }
        assertThat(recordedSpans(), hasItem(rootSpanWithName("trace")));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void should_mark_with_error() {
        TraceSpan span = Tracer.span("trace").start();
        try {
            throw new IllegalStateException("not good");
        } catch (Exception e) {
            span.markError(e);
        } finally {
            span.close();
        }
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void should_not_put_span_in_scope_if_noop() {
        Tracing.current().setNoop(true);
        try (TraceSpan s = Tracer.span("trace").start()) {
            LOG.info("some task");
            assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
        }
        assertThat(recordedSpans(), is(empty()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void should_not_put_span_in_scope_if_debug_only_and_debug_is_disabled() {
        TraceContext rootContext = TraceContext.newBuilder().traceId(1).spanId(1).debug(false).build();
        Tracing.current().currentTraceContext().maybeScope(rootContext);
        assertThat(Tracing.current().currentTraceContext().get(), is(rootContext));
        try (TraceSpan s = Tracer.debugSpan("trace").start()) {
            LOG.info("some task");
            assertThat(Tracing.current().currentTraceContext().get(), is(rootContext));
        }
        assertThat(recordedSpans(), is(empty()));
        assertThat(Tracing.current().currentTraceContext().get(), is(rootContext));
    }

    @Test
    public void should_put_span_in_scope_if_debug_only_and_debug_is_enabled() {
        TraceContext rootContext = TraceContext.newBuilder().traceId(1).spanId(1).debug(true).build();
        Tracing.current().currentTraceContext().maybeScope(rootContext);
        assertThat(Tracing.current().currentTraceContext().get(), is(rootContext));
        try (TraceSpan s = Tracer.debugSpan("trace").start()) {
            LOG.info("some task");
            assertThat(Tracing.current().currentTraceContext().get(), is(not(rootContext)));
        }
        assertThat(recordedSpans(), hasItem(spanWithName("trace")));
        assertThat(Tracing.current().currentTraceContext().get(), is(rootContext));
    }

    @Test
    public void should_wrap_supplier_execution() {
        MatcherAssert.assertThat(Tracer.span("trace").wrap(() -> "result"), is("result"));
        assertThat(recordedSpans(), hasItem(rootSpanWithName("trace")));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void should_propagate_supplier_runtime_exception() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> Tracer.span("trace").wrap((Supplier<String>) () -> {
                throw new IllegalStateException("not good");
            }));
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void should_wrap_runnable_execution() {
        Runnable runnable = mock(Runnable.class);
        Tracer.span("trace").wrap(runnable);
        verify(runnable).run();
        assertThat(recordedSpans(), hasItem(rootSpanWithName("trace")));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void should_propagate_runnable_runtime_exception() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> Tracer.span("trace").wrap((Runnable) () -> {
                throw new IllegalStateException("not good");
            }));
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void should_not_yield_any_error_if_tracing_is_not_initialized() {
        try {
            tearDownTraceContext();
            Runnable runnable = mock(Runnable.class);
            Tracer.span("trace").wrap(runnable);
            verify(runnable).run();
        } finally {
            initTraceContext();
        }
    }

    @Test
    public void should_propagate_supplier_exception() {
        assertThatExceptionOfType(Exception.class)
            .isThrownBy(() -> Tracer.span("trace").wrapChecked((CheckedSupplier<String, Exception>) () -> {
                throw new Exception("not good");
            }));
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void should_propagate_supplier_with_two_exceptions() {
        assertThatExceptionOfType(Exception.class)
            .isThrownBy(() -> Tracer.span("trace").wrapChecked((CheckedSupplier2<String, IOException, Exception>) () -> {
                throw new Exception("not good");
            }));
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void should_propagate_runnable_exception() {
        assertThatExceptionOfType(Exception.class)
            .isThrownBy(() -> Tracer.span("trace").wrapChecked((CheckedRunnable<Exception>) () -> {
                throw new Exception("not good");
            }));
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void should_propagate_runnable_with_two_exceptions() {
        assertThatExceptionOfType(Exception.class)
            .isThrownBy(() -> Tracer.span("trace").wrapChecked((Tracer.CheckedRunnable2<Exception, IOException>) () -> {
                throw new Exception("not good");
            }));
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }
}
