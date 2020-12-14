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

package com.hotels.molten.core;

import static java.util.Objects.requireNonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Hooks;
import reactor.core.scheduler.Schedulers;
import reactor.util.Loggers;

/**
 * Initializer for Molten. Does the followings:
 * <ul>
 * <li>Initializes SLF4J logging</li>
 * <li>Can enable {@link reactor.core.scheduler.Scheduler} metrics (requires Micrometer).</li>
 * </ul>
 *
 * For specific context propagation see the associated integration. e.g. {@link com.hotels.molten.core.mdc.MoltenMDC}.
 * Certain threading scenarios require explicit propagation solutions.
 * Context propagators can be registered with {@link MoltenCore#registerContextPropagator(String, Function)} and used with {@link MoltenCore#propagateContext()}.
 */
@Slf4j
@SuppressWarnings("rawtypes")
public final class MoltenCore {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    @Setter
    private static boolean metricsEnabled;
    private static final Map<String, Function<Publisher, Publisher>> CONTEXT_PROPAGATORS = new LinkedHashMap<>();
    private static Function<Publisher, Publisher> assembledContextPropagator = p -> p;

    private MoltenCore() {
        //utility class
    }

    /**
     * Initializes Reactor with expected logging, enabled metrics.
     */
    public static void initialize() {
        if (INITIALIZED.compareAndSet(false, true)) {
            LOG.info("Initializing Reactor with Molten...");
            Loggers.useSl4jLoggers();
            if (metricsEnabled) {
                Schedulers.shutdownNow();
                Schedulers.enableMetrics();
            }
        }
    }

    /**
     * Resets all Molten-Reactor integration, clearing context propagators, resetting all hooks.
     */
    public static void uninitialize() {
        if (INITIALIZED.get()) {
            CONTEXT_PROPAGATORS.clear();
            Hooks.resetOnEachOperator();
            Schedulers.resetOnScheduleHooks();
            INITIALIZED.set(false);
        }
    }

    public static synchronized void registerContextPropagator(String key, Function<Publisher, Publisher> propagator) {
        CONTEXT_PROPAGATORS.put(key, propagator);
        assembleContextPropagators();
    }

    /**
     * Removes a registered context propagator with the given key.
     *
     * @param key the key of the context propagator to be removed
     */
    public static synchronized void resetContextPropagator(String key) {
        CONTEXT_PROPAGATORS.remove(key);
        assembleContextPropagators();
    }

    /**
     * Provides an operator pointcut using the assembled registered context propagators.
     *
     * @param <T> the type of published entity left unchanged
     * @return the operator pointcut
     */
    public static <T> Function<Publisher<T>, Publisher<T>> propagateContext() {
        return assembledContextPropagator::apply;
    }

    private static void assembleContextPropagators() {
        assembledContextPropagator = CONTEXT_PROPAGATORS
            .values()
            .stream()
            .reduce(m -> m, Function::andThen);
        requireNonNull(assembledContextPropagator);
    }
}
