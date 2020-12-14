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

import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;

/**
 * A {@link Subscription} that never fuses so it can always propagate each call on its interface explicitly.
 *
 * @param <T> - type of the subscription
 */
public interface NonFusingSubscription<T> extends Subscription, CoreSubscriber<T>, Fuseable.QueueSubscription<T> {

    @Override
    default T poll() {
        return null;
    }

    @Override
    default int requestFusion(int i) {
        return Fuseable.NONE;
    }

    @Override
    default int size() {
        return 0;
    }

    @Override
    default boolean isEmpty() {
        return true;
    }

    @Override
    default void clear() {
        // NO-OP
    }

}
