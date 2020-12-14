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

package com.hotels.molten.cache;

import static com.google.common.base.Preconditions.checkState;

import java.time.Duration;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * A cached value with optional TTL.
 *
 * @param <V> type of value
 */
@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class CachedValue<V> {
    @NonNull
    private final V value;
    private final Duration ttl;

    public Optional<Duration> getTTL() {
        return Optional.ofNullable(ttl);
    }

    /**
     * Creates a cached value with a given TTL.
     *
     * @param value   the value to hold
     * @param ttl     the TTL of the value
     * @param <VALUE> the value type
     * @return the wrapped value
     */
    public static <VALUE> CachedValue<VALUE> of(VALUE value, Duration ttl) {
        checkState(ttl.toMillis() > 0, "TTL must be positive");
        return new CachedValue<>(value, ttl);
    }

    /**
     * Creates a cached value without TTL.
     *
     * @param value   the value to hold
     * @param <VALUE> the value type
     * @return the wrapped value
     */
    public static <VALUE> CachedValue<VALUE> just(VALUE value) {
        return new CachedValue<>(value, null);
    }
}
