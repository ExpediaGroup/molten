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

import java.util.Optional;

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

import com.hotels.molten.core.common.AbstractNonFusingSubscription;

/**
 * A {@link CoreSubscriber} that continues current trace context if any.
 */
@Slf4j
public final class TraceContextPropagatingSubscriber<T> extends AbstractNonFusingSubscription<T> {
    private final CurrentTraceContext currentTraceContext;
    private final TraceContext traceContext;
    private final Context context;

    private TraceContextPropagatingSubscriber(CoreSubscriber<? super T> subscriber, TraceContext traceContextInScope) {
        this(subscriber, traceContextInScope, Tracing.current().currentTraceContext());
    }

    public TraceContextPropagatingSubscriber(CoreSubscriber<? super T> subscriber, TraceContext traceContextInScope, CurrentTraceContext currentTraceContext) {
        super(subscriber);
        this.currentTraceContext = requireNonNull(currentTraceContext);
        this.traceContext = requireNonNull(traceContextInScope);
        this.context = subscriber.currentContext().put(TraceContext.class, traceContext);
        LOG.trace("Current tracecontext={}, context={}", traceContext, this.context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static CoreSubscriber decorate(CoreSubscriber sub) {
        Context context = sub.currentContext();
        TraceContext traceContextInScope = context.hasKey(TraceContext.class)
            ? context.get(TraceContext.class) // restore if we saved it
            : Optional.ofNullable(Tracing.current()).map(Tracing::currentTraceContext).map(CurrentTraceContext::get).orElse(null);
        return traceContextInScope != null ? new TraceContextPropagatingSubscriber(sub, traceContextInScope) : sub;
    }

    @Override
    protected void doOnSubscribe() {
        try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.traceContext)) {
            this.subscriber.onSubscribe(this);
        }
    }

    @Override
    public void request(long n) {
        try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.traceContext)) {
            this.subscription.request(n);
        }
    }

    @Override
    public void cancel() {
        try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.traceContext)) {
            this.subscription.cancel();
        }
    }

    @Override
    public void onNext(T o) {
        try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.traceContext)) {
            this.subscriber.onNext(o);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.traceContext)) {
            this.subscriber.onError(throwable);
        }
    }

    @Override
    public void onComplete() {
        try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.traceContext)) {
            this.subscriber.onComplete();
        }
    }

    @Override
    public Context currentContext() {
        return context;
    }
}
