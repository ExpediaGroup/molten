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

import java.util.function.Function;

import brave.Tracing;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Transformer to wrap upstream in a trace span. The span is opened when upstream is subscribed to and closed when it completed, ran to an error, or was cancelled.
 * <p>
 * <strong>Be sure to have tracing initialized with {@link MoltenTrace#initialize()}.</strong>
 * <p>
 * If tracing is disabled it leaves upstream unchanged. Please note that tracing context is still propagated.
 * <p>
 * If you want to isolate completely your span from downstream be sure to use {@link #withAsyncBoundary()}.
 * <p>
 * <strong>The transformer instance cannot be reused once applied on a stream and it was subscribed to.</strong> For performance considerations there's no guard for this.
 */
public final class TracingTransformer {
    private final Tracer tracer;
    private boolean asyncBoundary;

    private TracingTransformer(String spanName, boolean debugOnly) {
        if (debugOnly) {
            this.tracer = Tracer.debugSpan(spanName);
        } else {
            this.tracer = Tracer.span(spanName);
        }
    }

    /**
     * Creates a new tracing operator with a simple name.
     *
     * @param name the name
     * @return the operator
     */
    public static TracingTransformer span(String name) {
        return new TracingTransformer(name, false);
    }

    /**
     * Creates a new debug tracing operator with a simple name.
     *
     * @param name the name
     * @return the operator
     */
    public static TracingTransformer debugSpan(String name) {
        return new TracingTransformer(name, true);
    }

    /**
     * Separates the span in an async boundary downstream. This means downstream will be separated by publishing elements with {@link Schedulers#parallel()}.
     *
     * @return this transformer instance
     */
    public TracingTransformer withAsyncBoundary() {
        asyncBoundary = true;
        return this;
    }

    /**
     * Adds a tag to this span.
     *
     * @param key   the tag key
     * @param value the tag value
     * @return this transformer instance
     */
    public TracingTransformer tag(String key, Object value) {
        tracer.tag(key, value);
        return this;
    }

    /**
     * Creates a Mono transformer for this span.
     *
     * @param <T> the type of element in the Mono
     * @return the transformer function for Mono flow
     */
    public <T> Function<Mono<T>, Publisher<T>> forMono() {
        return source -> {
            Mono<T> tracedSource = source;
            if (isTracingEnabled()) {
                tracedSource = source
                    .doOnSubscribe(s -> tracer.createSpan())
                    .doOnError(tracer::tagError)
                    .doFinally(e -> tracer.finishSpan());
                if (asyncBoundary) {
                    tracedSource = tracedSource.publishOn(Schedulers.parallel());
                }
            }
            return tracedSource;
        };
    }

    /**
     * Creates a Flux transformer for this span.
     *
     * @param <T> the type of element in the Flux
     * @return the transformer function for Flux flow
     */
    public <T> Function<Flux<T>, Publisher<T>> forFlux() {
        return source -> {
            Flux<T> tracedSource = source;
            if (isTracingEnabled()) {
                tracedSource = source
                    .doOnSubscribe(s -> tracer.createSpan())
                    .doOnError(tracer::tagError)
                    .doFinally(e -> tracer.finishSpan());
                if (asyncBoundary) {
                    tracedSource = tracedSource.publishOn(Schedulers.parallel());
                }
            }
            return tracedSource;
        };
    }

    private boolean isTracingEnabled() {
        Tracing tracing = Tracing.current();
        return tracing != null && !tracing.isNoop();
    }
}
