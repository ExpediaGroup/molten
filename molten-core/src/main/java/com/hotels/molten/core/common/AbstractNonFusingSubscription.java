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

package com.hotels.molten.core.common;

import static java.util.Objects.requireNonNull;

import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;

/**
 * Abstract base class for creating context propagators to be used with
 * {@link com.hotels.molten.core.MoltenCore#registerContextPropagator(String, java.util.function.Function)}.
 * See example {@code com.hotels.molten.core.mdc.MDCContextPropagatingSubscriber}.
 * @param <T> the actual element type
 */
public abstract class AbstractNonFusingSubscription<T> implements NonFusingSubscription<T>, Scannable {
    protected final CoreSubscriber<? super T> subscriber;
    protected Subscription subscription;

    protected AbstractNonFusingSubscription(CoreSubscriber<? super T> subscriber) {
        this.subscriber = requireNonNull(subscriber);
    }

    protected abstract void doOnSubscribe();

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        doOnSubscribe();
    }

    @Override
    public Object scanUnsafe(Scannable.Attr key) {
        Object s;
        if (key == Attr.PARENT) {
            s = this.subscription;
        } else {
            s = key == Attr.ACTUAL
                ? this.subscriber
                : null;
        }
        return s;
    }
}
