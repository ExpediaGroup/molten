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

import java.util.Queue;
import java.util.function.Function;

import brave.Tracing;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.Fuseable;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Schedulers;

import com.hotels.molten.core.MoltenCore;

@Slf4j
public final class MoltenTrace {
    private static final String HOOK_KEY = "molten.trace.hook";

    private MoltenTrace() {
        //utility class
    }


    /**
     * Initializes tracing in Reactor.
     *
     * Ensures tracing context is propagated when switching scheduler threads or when queueing and retrieving tasks in Reactor.
     */
    @SuppressWarnings("unchecked")
    public static void initialize() {
        uninitialize();
        LOG.info("Integrating tracing with Molten");
        var currentTracing = Tracing.current();
        requireNonNull(currentTracing, "Tracing must be already initialized");
        Schedulers.onScheduleHook(HOOK_KEY, runnable -> new TraceContextPropagatingRunnable(runnable, currentTracing.currentTraceContext()));
        Hooks.addQueueWrapper(HOOK_KEY, q -> new TraceContextPropagatorQueue((Queue<Object>) q, currentTracing.currentTraceContext()));
        MoltenCore.registerContextPropagator(HOOK_KEY, MoltenTrace.propagate()::apply);
    }

    /**
     * Initializes tracing in Reactor.
     *
     * @param unused unused parameter
     * @deprecated use {@link #initialize()}
     */
    @Deprecated
    public static void initialize(boolean unused) {
        initialize();
    }


    /**
     * Reset all Molten Trace - Reactor integration.
     */
    public static void uninitialize() {
        MoltenCore.resetContextPropagator(HOOK_KEY);
        Hooks.removeQueueWrapper(HOOK_KEY);
        Schedulers.resetOnScheduleHook(HOOK_KEY);
    }

    /**
     * Creates an operator pointcut which propagates trace context.
     * Should not be used directly but through {@link MoltenCore#propagateContext()}.
     *
     * @param <T> the type of published entity left unchanged
     * @return the operator pointcut
     */
    @SuppressWarnings("unchecked")
    public static <T> Function<Publisher<T>, Publisher<T>> propagate() {
        return (Function<Publisher<T>, Publisher<T>>) Operators.<T, T>liftPublisher((p, sub) ->
            (p instanceof Fuseable.ScalarCallable)
                ? sub
                : TraceContextPropagatingSubscriber.decorate(sub));
    }
}
