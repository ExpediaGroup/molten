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

package com.hotels.molten.core.mdc;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.Queue;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;
import reactor.core.Fuseable;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Schedulers;

import com.hotels.molten.core.MoltenCore;

/**
 * Setup for MDC handling with Molten.
 */
@Slf4j
public final class MoltenMDC {
    private static final String HOOK_KEY = "molten.mdc.hook";

    private MoltenMDC() {
        //utility class
    }

    /**
     * Initializes MDC propagation in Reactor.
     *
     * Ensures MDC is propagated when switching schedulers or when queueing and retrieving tasks in Reactor.
     */
    @SuppressWarnings("unchecked")
    public static void initialize() {
        uninitialize();
        LOG.info("Integrating MDC with Molten");
        Schedulers.onScheduleHook(HOOK_KEY, MDCCopyingAction::new);
        Hooks.addQueueWrapper(HOOK_KEY, q -> new MdcContextPropagatingQueue((Queue<Object>) q));
        MoltenCore.registerContextPropagator(HOOK_KEY, MoltenMDC.propagate()::apply);
    }

    /**
     * Reset all Molten MDC - Reactor integration.
     */
    public static void uninitialize() {
        MoltenCore.resetContextPropagator(HOOK_KEY);
        Hooks.removeQueueWrapper(HOOK_KEY);
        Schedulers.resetOnScheduleHook(HOOK_KEY);
    }

    /**
     * Creates an operator pointcut which propagates MDC context with the values at flow execution time.
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
                : MDCContextPropagatingSubscriber.decorate(sub));
    }

    private static final class MDCCopyingAction implements Runnable {
        private final Runnable delegate;
        private final Map<String, String> savedContextMap;

        private MDCCopyingAction(Runnable delegate) {
            this.delegate = requireNonNull(delegate);
            savedContextMap = MDC.getCopyOfContextMap();
            LOG.trace("saved MDC {}", savedContextMap);
        }

        @Override
        public void run() {
            if (savedContextMap != null) {
                LOG.trace("restoring MDC {}", savedContextMap);
                MDC.setContextMap(savedContextMap);
                try {
                    delegate.run();
                } finally {
                    MDC.clear();
                }
            } else {
                LOG.trace("no MDC to restore");
                delegate.run();
            }
        }
    }
}
