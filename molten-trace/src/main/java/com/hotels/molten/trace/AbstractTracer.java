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

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import brave.Span;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.propagation.TraceContext;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for creating tracers.
 */
@Slf4j
@ToString(of = {"spanName", "tags"})
abstract class AbstractTracer {
    private final String spanName;
    private final boolean debugOnly;
    private Map<String, Object> tags;
    private volatile Span span;
    private volatile SpanInScope spanInScope;

    AbstractTracer(String spanName, boolean debugOnly) {
        this.spanName = requireNonNull(spanName);
        this.debugOnly = debugOnly;
    }

    void createSpan() {
        Tracing current = Tracing.current();
        if (current != null) {
            TraceContext parent = current.currentTraceContext().get();
            if (tracingEnabled(parent)) {
                createSpanAndPutInScope(current, parent);
            } else {
                LOG.debug("Skipping debug only span {}", spanName);
            }
        }
    }

    private synchronized void createSpanAndPutInScope(Tracing current, TraceContext parent) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("starting span {} in trace context {}", spanName, parent);
        }
        span = parent == null ? Tracing.currentTracer().newTrace() : Tracing.currentTracer().newChild(parent);
        if (!span.isNoop()) {
            span.name(spanName);
            addTags();
            span.start();
            spanInScope = Tracing.currentTracer().withSpanInScope(span);
            if (LOG.isTraceEnabled()) {
                LOG.trace("entered trace context {}", current.currentTraceContext().get());
            }
        }
    }

    private boolean tracingEnabled(TraceContext parent) {
        return !debugOnly || (parent != null && parent.debug());
    }

    synchronized void finishSpan() {
        if (span != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("finishing span {} in trace context {}", spanName, Tracing.current().currentTraceContext().get());
            }
            if (!span.isNoop()) {
                span.finish();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("leaving trace context {}", Tracing.current().currentTraceContext().get());
                }
                spanInScope.close();
            }
            span = null;
        } else {
            LOG.debug("No span to finish or already finished");
        }
    }

    void tagError(Throwable e) {
        Span s = span;
        if (s != null) {
            s.error(e);
        }
    }

    void addTag(String key, Object value) {
        Span s = span;
        if (s != null) {
            s.tag(key, String.valueOf(value));
        } else {
            if (tags == null) {
                tags = new HashMap<>();
            }
            tags.put(key, value);
        }
    }

    String getSpanName() {
        return spanName;
    }

    Map<String, Object> getTags() {
        return tags;
    }

    private void addTags() {
        if (tags != null) {
            tags.forEach((key, value) -> span.tag(key, String.valueOf(value)));
        }
    }
}
