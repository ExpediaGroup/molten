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

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import lombok.extern.slf4j.Slf4j;

/**
 * A runnable which propagates trace context from creation time.
 */
@Slf4j
final class TraceContextPropagatingRunnable implements Runnable {
    private final Runnable delegate;
    private final TraceContext traceContext;
    private final CurrentTraceContext currentTraceContext;

    TraceContextPropagatingRunnable(Runnable delegate, CurrentTraceContext currentTraceContext) {
        this.delegate = requireNonNull(delegate);
        this.currentTraceContext = requireNonNull(currentTraceContext);
        this.traceContext = currentTraceContext.get();
        LOG.trace("saved trace context {}", traceContext);
    }

    @Override
    public void run() {
        try (CurrentTraceContext.Scope scope = currentTraceContext.maybeScope(traceContext)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("switched to trace context {}", currentTraceContext.get());
            }
            delegate.run();
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("restored trace context {}", currentTraceContext.get());
        }
    }

}
