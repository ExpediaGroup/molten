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

package com.hotels.molten.http.client;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Duration;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Configuration options for HTTP connections.
 */
@EqualsAndHashCode
@ToString
@Getter
public final class ConnectionSettings {
    private final Duration timeout;
    private final Duration keepAliveIdle;
    private final Duration maxLife;
    private final boolean retryOnConnectionFailure;

    private ConnectionSettings(ConnectionSettingsBuilder builder) {
        timeout = builder.timeout;
        keepAliveIdle = builder.keepAliveIdle;
        retryOnConnectionFailure = builder.retryOnConnectionFailure;
        maxLife = builder.maxLife;
    }

    /**
     * Creates a {@link ConnectionSettings} builder with defaults.
     *
     * @return the builder
     */
    public static ConnectionSettingsBuilder builder() {
        return new ConnectionSettingsBuilder();
    }

    /**
     * Builder for {@link ExpectedLoad}.
     * Defaults are 1 second timeout, 15 minutes keeping alive idle connections, 15 minutes max life and retrying connection failures.
     */
    public static final class ConnectionSettingsBuilder {
        private Duration timeout = Duration.ofSeconds(1);
        private Duration keepAliveIdle = Duration.ofMinutes(15);
        private Duration maxLife = Duration.ofMinutes(15);
        private boolean retryOnConnectionFailure = true;

        private ConnectionSettingsBuilder() {
            // builder pattern
        }

        /**
         * Sets the maximum time to wait for a new connection.
         * Defaults to 1 second.
         *
         * @param timeout the timeout for connection
         * @return this builder
         */
        public ConnectionSettingsBuilder timeout(Duration timeout) {
            checkArgument(timeout.toMillis() > 0, "Connection timeout must be positive");
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum time to keep idle connections.
         * Defaults to 15 minutes.
         *
         * @param keepAlive the timeout for idle connections
         * @return this builder
         */
        public ConnectionSettingsBuilder keepAliveIdle(Duration keepAlive) {
            checkArgument(keepAlive.toMillis() > 0, "Keep alive timeout must be positive");
            this.keepAliveIdle = keepAlive;
            return this;
        }

        /**
         * Sets the maximum time to keep connections alive.
         * Defaults to 15 minutes.
         *
         * @param maxLife the maximum time connection are kept
         * @return this builder
         */
        public ConnectionSettingsBuilder maxLife(Duration maxLife) {
            checkArgument(maxLife.toMillis() > 0, "Max life time must be positive");
            this.maxLife = maxLife;
            return this;
        }

        /**
         * Whether to retry (once) recoverable connection failures (i.e. connection reset) or not.
         * Defaults to enabled.
         *
         * @param enabled enable connection retries on failures
         * @return this builder
         */
        public ConnectionSettingsBuilder retryOnConnectionFailure(boolean enabled) {
            retryOnConnectionFailure = enabled;
            return this;
        }

        /**
         * Builds the {@link ConnectionSettings} instance based on this builder.
         *
         * @return the {@link ConnectionSettings} instance
         */
        public ConnectionSettings build() {
            return new ConnectionSettings(this);
        }
    }
}
