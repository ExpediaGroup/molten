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

package com.hotels.molten.http.client.tracking;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.function.Supplier;

import lombok.NonNull;
import lombok.Value;

/**
 * Configuration for request tracking.
 */
public final class RequestTracking {
    private final Supplier<Optional<TrackingHeader>> requestIdSupplier;
    private final Supplier<Optional<TrackingHeader>> sessionIdSupplier;
    private final Supplier<Optional<TrackingHeader>> clientIdSupplier;

    private RequestTracking(Builder builder) {
        requestIdSupplier = requireNonNull(builder.requestIdSupplier);
        sessionIdSupplier = requireNonNull(builder.sessionIdSupplier);
        clientIdSupplier = requireNonNull(builder.clientIdSupplier);
    }

    public Supplier<Optional<TrackingHeader>> getRequestIdSupplier() {
        return requestIdSupplier;
    }

    public Supplier<Optional<TrackingHeader>> getSessionIdSupplier() {
        return sessionIdSupplier;
    }

    public Supplier<Optional<TrackingHeader>> getClientIdSupplier() {
        return clientIdSupplier;
    }

    /**
     * Creates an empty {@link RequestTracking} builder. All the default suppliers are returning empty values.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RequestTracking}.
     */
    public static final class Builder {
        private Supplier<Optional<TrackingHeader>> requestIdSupplier = Optional::empty;
        private Supplier<Optional<TrackingHeader>> sessionIdSupplier = Optional::empty;
        private Supplier<Optional<TrackingHeader>> clientIdSupplier = Optional::empty;

        private Builder() {
        }

        /**
         * Sets the request ID supplier.
         *
         * @param val the request ID supplier
         * @return this builder instance
         */
        public Builder requestIdSupplier(Supplier<Optional<TrackingHeader>> val) {
            requestIdSupplier = requireNonNull(val);
            return this;
        }

        /**
         * Sets the session ID supplier.
         *
         * @param val the session ID supplier
         * @return this builder instance
         */
        public Builder sessionIdSupplier(Supplier<Optional<TrackingHeader>> val) {
            sessionIdSupplier = requireNonNull(val);
            return this;
        }

        /**
         * Sets the client ID supplier.
         *
         * @param val the client ID supplier
         * @return this builder instance
         */
        public Builder clientIdSupplier(Supplier<Optional<TrackingHeader>> val) {
            clientIdSupplier = requireNonNull(val);
            return this;
        }

        public RequestTracking build() {
            return new RequestTracking(this);
        }
    }

    /**
     * A request tracking header with name and value.
     */
    @Value
    public static class TrackingHeader {
        @NonNull
        private final String name;
        @NonNull
        private final String value;
    }
}
