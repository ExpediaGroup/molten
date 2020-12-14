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
import static java.util.Objects.requireNonNull;

/**
 * Configuration for bulkhead. Determines call isolation and concurrency.
 */
final class BulkheadConfiguration {
    private final String isolationGroupName;
    private final int maxConcurrency;

    private BulkheadConfiguration(Builder builder) {
        isolationGroupName = requireNonNull(builder.isolationGroupName);
        maxConcurrency = builder.maxConcurrency;
    }

    public String getIsolationGroupName() {
        return isolationGroupName;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private static final int DEFAULT_MAX_CONCURRENCY = 10;
        private String isolationGroupName;
        private int maxConcurrency = DEFAULT_MAX_CONCURRENCY;

        private Builder() {
        }

        /**
         * Sets the name of the isolation group.
         *
         * @param isolationGroupName the name of the isolation group
         * @return this builder instance
         */
        public Builder isolationGroupName(String isolationGroupName) {
            checkArgument(isolationGroupName != null, "The isolation group name must be non-null.");
            this.isolationGroupName = isolationGroupName;
            return this;
        }

        /**
         * Sets the maximum number of concurrent executions.
         *
         * @param maxConcurrency the maximum number of conccurrent executions
         * @return this builder instance
         */
        public Builder maxConcurrency(int maxConcurrency) {
            checkArgument(maxConcurrency > 0, "Concurrency must be positive.");
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public BulkheadConfiguration build() {
            return new BulkheadConfiguration(this);
        }
    }
}
