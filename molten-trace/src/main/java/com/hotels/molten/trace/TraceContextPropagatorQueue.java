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

import java.util.Queue;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import lombok.NonNull;

import com.hotels.molten.core.common.ContextPropagatorQueue;

public class TraceContextPropagatorQueue extends ContextPropagatorQueue<TraceContext> {
    private final CurrentTraceContext currentTraceContext;

    public TraceContextPropagatorQueue(@NonNull Queue<Object> delegate, @NonNull CurrentTraceContext currentTraceContext) {
        super(delegate);
        this.currentTraceContext = currentTraceContext;
    }

    @Override
    protected TraceContext retrieveContext() {
        return currentTraceContext.get();
    }

    @Override
    protected void restoreContext(TraceContext traceContext) {
        if (traceContext != null) {
            currentTraceContext.maybeScope(traceContext);
        }
    }
}
