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

package com.hotels.molten.trace.test;

import static com.hotels.molten.trace.test.TracingTestSupport.capturedSpans;
import static com.hotels.molten.trace.test.TracingTestSupport.resetCapturedSpans;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import brave.ScopedSpan;
import brave.Tracing;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import zipkin2.Span;

import com.hotels.molten.core.MoltenCore;
import com.hotels.molten.trace.MoltenTrace;

/**
 * Abstract base class for testing reactive tracing.
 */
@Slf4j
public abstract class AbstractTracingTest {

    @BeforeClass
    public void initTraceContext() {
        MoltenCore.initialize();
        TracingTestSupport.initialize(false);
        MoltenTrace.initialize();
    }

    @AfterClass
    public void tearDownTraceContext() {
        MoltenTrace.uninitialize();
        TracingTestSupport.cleanUp();
    }

    @BeforeMethod
    public void clearTraceContext() {
        TracingTestSupport.resetCapturedSpans();
        Tracing current = Tracing.current();
        if (current != null) {
            current.currentTraceContext().maybeScope(null);
            current.setNoop(false);
        }
    }

    protected List<Span> recordedSpans() {
        return TracingTestSupport.capturedSpans();
    }

    protected void assertTraceContextIsPropagated(Mono<?> publisher) {
        resetCapturedSpans();
        ScopedSpan outer = Tracing.currentTracer().startScopedSpan("outer");
        try {
            String expectedTraceId = Tracing.currentTracer().currentSpan().context().traceIdString();
            LOG.info("current traceId={}", expectedTraceId);
            StepVerifier.create(publisher
                .map(r -> Tracing.currentTracer().currentSpan().context().traceIdString())
                .doOnNext(id -> LOG.info("after publish traceId={}", id))
            )
                .thenAwait()
                .expectNext(expectedTraceId) // verifies that the trace was not broken
                .verifyComplete();
        } finally {
            outer.finish();
        }
        assertThat(capturedSpans())
            .extracting(Span::name)
            .contains("outer");
    }
}
