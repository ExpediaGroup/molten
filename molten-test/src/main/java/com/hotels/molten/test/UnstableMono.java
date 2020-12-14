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

package com.hotels.molten.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import reactor.core.publisher.Mono;

/**
 * Creates an unstable {@link Mono} which emits different events per subscription.
 * Purely for testing purposes.
 *
 * @param <VALUE> the type of elements to emit
 */
public final class UnstableMono<VALUE> {
    private final Mono<VALUE> mono;
    private final List<Mono<VALUE>> emits;
    private AtomicInteger index = new AtomicInteger();
    private AtomicInteger subscriptionCount = new AtomicInteger();

    private UnstableMono(List<Mono<VALUE>> events) {
        this.emits = List.copyOf(events);
        mono = Mono.create(e -> {
            subscriptionCount.incrementAndGet();
            int idx = index.getAndIncrement();
            if (emits.size() > idx) {
                emits.get(idx).subscribe(e::success, e::error, e::success);
            } else {
                e.success();
            }
        });
    }

    /**
     * Get the Mono emitting the set up events. Please note that it is stateful and must be recreated once subscribed to.
     * Emits all events in order till it runs out of them. After that it just completes.
     *
     * @return the mono
     */
    public Mono<VALUE> mono() {
        return mono;
    }

    /**
     * Get the number of subscriptions happened so far.
     *
     * @return the number of subscriptions
     */
    public int getSubscriptionCount() {
        return subscriptionCount.get();
    }

    public static <V> UnstableMonoBuilder<V> builder() {
        return new UnstableMonoBuilder<>();
    }

    /**
     * Builder for {@link UnstableMono}.
     *
     * @param <V> the type of elements
     */
    public static class UnstableMonoBuilder<V> {
        private List<Mono<V>> emits = new ArrayList<>();

        /**
         * Creates the unstable {@link Mono} based on this builder.
         *
         * @return the mono
         */
        public UnstableMono<V> create() {
            return new UnstableMono<>(emits);
        }

        /**
         * For next subscription emit an error.
         *
         * @param e the error to emit
         * @return this builder
         */
        public UnstableMonoBuilder<V> thenError(Throwable e) {
            emits.add(Mono.error(e));
            return this;
        }

        /**
         * For next subscription emit an element.
         *
         * @param e the element to emit
         * @return this builder
         */
        public UnstableMonoBuilder<V> thenElement(V e) {
            emits.add(Mono.just(e));
            return this;
        }

        /**
         * For next subscription complete without element.
         *
         * @return this builder
         */
        public UnstableMonoBuilder<V> thenEmpty() {
            emits.add(Mono.empty());
            return this;
        }
    }
}
