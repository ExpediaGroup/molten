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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import brave.Tracing;
import brave.propagation.TraceContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import lombok.extern.slf4j.Slf4j;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hotels.molten.test.mockito.ReactiveMockitoAnnotations;
import com.hotels.molten.trace.test.AbstractTracingTest;
import com.hotels.molten.trace.test.SpanMatcher;

/**
 * Unit test for {@link TracingTransformer}.
 */
@Slf4j
public class TracingTransformerTest extends AbstractTracingTest {
    private Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    @Mock
    private Appender<ILoggingEvent> appender;
    @Captor
    private ArgumentCaptor<ILoggingEvent> captorLoggingEvent;

    @BeforeMethod
    public void init() {
        ReactiveMockitoAnnotations.initMocks(this);
        when(appender.getName()).thenReturn("MOCK");
        rootLogger.addAppender(appender);
    }

    @AfterClass
    public void tearDown() {
        rootLogger.detachAppender(appender);
    }

    @Test
    public void shouldCreateSpanWithSimpleName() {
        StepVerifier.create(Mono.just(1)
            .transform(TracingTransformer.span("trace").forMono()))
            .expectNext(1)
            .verifyComplete();
        assertThat(recordedSpans(), hasItem(rootSpanWithName("trace")));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void shouldCreateSpanWithTags() {
        StepVerifier.create(Mono.just(1)
            .transform(TracingTransformer.span("trace").tag("number", 2).forMono()))
            .expectNext(1)
            .verifyComplete();
        assertThat(recordedSpans(), hasItem(SpanMatcher.builder().name("trace").tag("number", "2").build()));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void shouldCreateDebugSpanWithSimpleName() {
        TraceContext rootContext = TraceContext.newBuilder().traceId(1).spanId(1).debug(true).build();
        Tracing.current().currentTraceContext().maybeScope(rootContext);
        StepVerifier.create(Mono.just(1)
            .transform(TracingTransformer.debugSpan("trace").forMono()))
            .expectNext(1)
            .verifyComplete();
        assertThat(recordedSpans(), hasItem(spanWithName("trace")));
        assertThat(Tracing.current().currentTraceContext().get(), is(rootContext));
    }

    @Test
    public void shouldNotCreateDebugSpan() {
        StepVerifier.create(Mono.just(1)
            .transform(TracingTransformer.debugSpan("trace").forMono()))
            .expectNext(1)
            .verifyComplete();
        assertThat(recordedSpans(), is(empty()));

        verify(appender, times(2)).doAppend(captorLoggingEvent.capture());
        ILoggingEvent firstLoggingEvent = captorLoggingEvent.getAllValues().get(0);
        assertThat(firstLoggingEvent.getLevel(), is(Level.DEBUG));
        assertThat(firstLoggingEvent.getFormattedMessage(), is("Skipping debug only span trace"));

        ILoggingEvent secondLoggingEvent = captorLoggingEvent.getAllValues().get(1);
        assertThat(secondLoggingEvent.getLevel(), is(Level.DEBUG));
        assertThat(secondLoggingEvent.getFormattedMessage(), is("No span to finish or already finished"));
    }

    @Test
    public void showSideEffectOfAsyncBoundary() {
        StepVerifier.create(Mono.just(1)
            .doOnNext(i -> LOG.debug("upstream {}", i))
            .transform(TracingTransformer.span("trace").withAsyncBoundary().forMono())
            .doOnNext(i -> LOG.debug("downstream {}", i)))
            .thenAwait()
            .expectNext(1)
            .verifyComplete();
        assertThat(recordedSpans(), hasItem(rootSpanWithName("trace")));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }

    @Test
    public void shouldSupportFlux() {
        StepVerifier.create(Flux.just(1, 2, 3)
            .transform(TracingTransformer.span("trace").forFlux()))
            .expectNext(1, 2, 3)
            .verifyComplete();
        assertThat(recordedSpans(), hasItem(rootSpanWithName("trace")));
        assertThat(Tracing.current().currentTraceContext().get(), is(nullValue()));
    }
}
