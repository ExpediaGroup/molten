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

import java.util.function.Supplier;

/**
 * Creates a tracer which can start and finish a span.
 * When started, it also puts the span in scope. Restores scope when closed.
 * The tracer cannot be reused once started but tags/error can be added later as well.
 */
public final class Tracer extends AbstractTracer {
    private Tracer(String spanName, boolean debugOnly) {
        super(spanName, debugOnly);
    }

    /**
     * Creates a new tracer with a given name.
     *
     * @param name the name to use for span
     * @return the operator
     */
    public static Tracer span(String name) {
        return new Tracer(name, false);
    }

    /**
     * Creates a new tracer with a given name which only traces when debug is enabled.
     *
     * @param name the name to use for span
     * @return the operator
     */
    public static Tracer debugSpan(String name) {
        return new Tracer(name, true);
    }

    /**
     * Adds a tag to the traced span.
     *
     * @param key   the tag key
     * @param value the tag value
     * @return a new operator with tag added
     */
    @SuppressWarnings("unchecked")
    public Tracer tag(String key, Object value) {
        addTag(key, value);
        return this;
    }

    /**
     * Starts the span and puts it in scope.
     */
    public TraceSpan start() {
        createSpan();
        return this.new TraceSpan();
    }

    /**
     * Wraps a supplier execution in a span and returns the result.
     * <br />
     * Propagates all exceptions during supplier execution. Also marks span with the exception thrown.
     *
     * @param supplier the supplier to get result from
     * @param <T>      the return type
     * @return the result
     */
    public <T> T wrap(Supplier<T> supplier) {
        TraceSpan span = start();
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            span.markError(e);
            throw e;
        } finally {
            span.close();
        }
    }

    /**
     * Wraps a checked supplier execution in a span and returns the result.
     * <br />
     * Propagates all exceptions during supplier execution. Also marks span with the exception thrown.
     *
     * @param supplier the supplier to get result from
     * @param <T>      the return type
     * @param <EX>     the type of exception thrown
     * @return the result
     * @throws EX thrown by the implementation
     */
    public <T, EX extends Exception> T wrapChecked(CheckedSupplier<T, EX> supplier) throws EX {
        TraceSpan span = start();
        try {
            return supplier.get();
        } catch (Exception e) {
            span.markError(e);
            throw e;
        } finally {
            span.close();
        }
    }

    /**
     * Wraps a checked supplier execution in a span and returns the result.
     * <br />
     * Propagates all exceptions during supplier execution. Also marks span with the exception thrown.
     *
     * @param supplier   the supplier to get result from
     * @param <T>        the return type
     * @param <EX>       the type of exception thrown
     * @param <OTHER_EX> the type of other exception thrown
     * @return the result
     * @throws EX       thrown by the implementation
     * @throws OTHER_EX thrown by the implementation
     */
    public <T, EX extends Exception, OTHER_EX extends Exception> T wrapChecked(CheckedSupplier2<T, EX, OTHER_EX> supplier) throws EX, OTHER_EX {
        TraceSpan span = start();
        try {
            return supplier.get();
        } catch (Exception e) {
            span.markError(e);
            throw e;
        } finally {
            span.close();
        }
    }

    /**
     * Wraps a runnable execution in a span.
     * <br />
     * Propagates all exceptions during runnable execution. Also marks span with the exception thrown.
     *
     * @param runnable the runnable to run
     */
    public void wrap(Runnable runnable) {
        TraceSpan span = start();
        try {
            runnable.run();
        } catch (RuntimeException e) {
            span.markError(e);
            throw e;
        } finally {
            span.close();
        }
    }

    /**
     * Wraps a checked runnable execution in a span.
     * <br />
     * Propagates all exceptions during runnable execution. Also marks span with the exception thrown.
     *
     * @param runnable the runnable to run
     * @param <EX>     the type of exception thrown
     * @throws EX thrown by the implementation
     */
    public <EX extends Exception> void wrapChecked(CheckedRunnable<EX> runnable) throws EX {
        TraceSpan span = start();
        try {
            runnable.run();
        } catch (Exception e) {
            span.markError(e);
            throw e;
        } finally {
            span.close();
        }
    }

    /**
     * Wraps a checked runnable execution in a span.
     * <br />
     * Propagates all exceptions during runnable execution. Also marks span with the exception thrown.
     *
     * @param runnable   the runnable to run
     * @param <EX>       the type of exception thrown
     * @param <OTHER_EX> the type of other exception thrown
     * @throws EX       thrown by the implementation
     * @throws OTHER_EX thrown by the implementation
     */
    public <EX extends Exception, OTHER_EX extends Exception> void wrapChecked(CheckedRunnable2<EX, OTHER_EX> runnable) throws EX, OTHER_EX {
        TraceSpan span = start();
        try {
            runnable.run();
        } catch (Exception e) {
            span.markError(e);
            throw e;
        } finally {
            span.close();
        }
    }

    /**
     * An ongoing span.
     */
    public final class TraceSpan implements AutoCloseable {
        /**
         * Finishes the span and restores scope.
         */
        @Override
        public void close() {
            finishSpan();
        }

        /**
         * Tags the span with an error.
         *
         * @param e the error
         */
        public void markError(Throwable e) {
            tagError(e);
        }
    }

    /**
     * Similar to {@link Supplier} but can throw {@link Exception}s.
     *
     * @param <T>  Result type of this supplier
     * @param <EX> the type of exception thrown
     */
    public interface CheckedSupplier<T, EX extends Exception> {

        /**
         * Gets the result.
         *
         * @return a result
         * @throws EX thrown by the implementation
         */
        T get() throws EX;
    }

    /**
     * Similar to {@link Supplier} but can throw {@link Exception}s.
     *
     * @param <T>        Result type of this supplier
     * @param <EX>       the type of exception thrown
     * @param <OTHER_EX> the type of other exception thrown
     */
    public interface CheckedSupplier2<T, EX extends Exception, OTHER_EX extends Exception> {

        /**
         * Gets the result.
         *
         * @return a result
         * @throws EX       thrown by the implementation
         * @throws OTHER_EX thrown by the implementation
         */
        T get() throws EX, OTHER_EX;
    }

    /**
     * Similar to {@link Runnable} but can throw {@link Exception}s.
     *
     * @param <EX> the type of exception thrown
     */
    @FunctionalInterface
    public interface CheckedRunnable<EX extends Exception> {
        /**
         * Executes the action.
         *
         * @throws EX thrown by the implementation
         */
        void run() throws EX;
    }

    /**
     * Similar to {@link Runnable} but can throw two kind of {@link Exception}s.
     *
     * @param <EX>       the type of exception thrown
     * @param <OTHER_EX> the type of other exception thrown
     */
    @FunctionalInterface
    public interface CheckedRunnable2<EX extends Exception, OTHER_EX extends Exception> {
        /**
         * Executes the action.
         *
         * @throws EX       thrown by the implementation
         * @throws OTHER_EX thrown by the implementation
         */
        void run() throws EX, OTHER_EX;
    }
}
