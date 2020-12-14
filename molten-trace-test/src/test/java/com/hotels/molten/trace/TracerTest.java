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
import org.testng.annotations.Test;

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
    public void shouldWrapCallToSpan() {
        try (TraceSpan s = Tracer.span("trace").start()) {
            LOG.info("some task");
        }
        assertThat(recordedSpans(), hasItem(rootSpanWithName("trace")));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void shouldMarkWithError() {
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
    public void shouldNotPutSpanInScopeIfNoop() {
        Tracing.current().setNoop(true);
        try (TraceSpan s = Tracer.span("trace").start()) {
            LOG.info("some task");
            assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
        }
        assertThat(recordedSpans(), is(empty()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void shouldNotPutSpanInScopeIfDebugOnlyAndDebugIsDisabled() {
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
    public void shouldPutSpanInScopeIfDebugOnlyAndDebugIsEnabled() {
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
    public void shouldWrapSupplierExecution() {
        MatcherAssert.assertThat(Tracer.span("trace").wrap(() -> "result"), is("result"));
        assertThat(recordedSpans(), hasItem(rootSpanWithName("trace")));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void shouldPropagateSupplierRuntimeException() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> Tracer.span("trace").wrap((Supplier<String>) () -> {
                throw new IllegalStateException("not good");
            }));
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void shouldWrapRunnableExecution() {
        Runnable runnable = mock(Runnable.class);
        Tracer.span("trace").wrap(runnable);
        verify(runnable).run();
        assertThat(recordedSpans(), hasItem(rootSpanWithName("trace")));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void shouldPropagateRunnableRuntimeException() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> Tracer.span("trace").wrap((Runnable) () -> {
                throw new IllegalStateException("not good");
            }));
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void shouldNotYieldAnyErrorIfTracingIsNotInitialized() {
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
    public void shouldPropagateSupplierException() {
        assertThatExceptionOfType(Exception.class)
            .isThrownBy(() -> Tracer.span("trace").wrapChecked((CheckedSupplier<String, Exception>) () -> {
                throw new Exception("not good");
            }));
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void shouldPropagateSupplierWithTwoExceptions() {
        assertThatExceptionOfType(Exception.class)
            .isThrownBy(() -> Tracer.span("trace").wrapChecked((CheckedSupplier2<String, IOException, Exception>) () -> {
                throw new Exception("not good");
            }));
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void shouldPropagateRunnableException() {
        assertThatExceptionOfType(Exception.class)
            .isThrownBy(() -> Tracer.span("trace").wrapChecked((CheckedRunnable<Exception>) () -> {
                throw new Exception("not good");
            }));
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void shouldPropagateRunnableWithTwoExceptions() {
        assertThatExceptionOfType(Exception.class)
            .isThrownBy(() -> Tracer.span("trace").wrapChecked((Tracer.CheckedRunnable2<Exception, IOException>) () -> {
                throw new Exception("not good");
            }));
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("error", "not good").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }
}
