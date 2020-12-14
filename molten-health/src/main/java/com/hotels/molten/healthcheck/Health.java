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

package com.hotels.molten.healthcheck;

import static java.util.Objects.requireNonNull;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import lombok.Value;

/**
 * The current health status of a component.
 */
@Value
public final class Health {
    private static final String FUNCTIONAL = "The component is fine";
    private final Status status;
    private final String message;
    private final ZonedDateTime timestamp = ZonedDateTime.now(ZoneOffset.UTC);

    private Health(Builder builder) {
        status = builder.status;
        message = builder.message;
    }

    /**
     * Returns true if and only if the component is healthy.
     *
     * @return true if the status is {@link Status#UP}, otherwise false
     */
    public boolean healthy() {
        return status == Status.UP;
    }

    /**
     * The health status.
     *
     * @return the health status
     */
    public Status status() {
        return status;
    }

    /**
     * The message of this health.
     *
     * @return the message
     */
    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    /**
     * The timestamp when this health was created.
     *
     * @return the creation time
     */
    public ZonedDateTime timestamp() {
        return timestamp;
    }

    /**
     * Creates a {@link Health} builder.
     *
     * @param status the status
     * @return the builder
     */
    public static Health.Builder builder(Status status) {
        return new Builder(status);
    }

    /**
     * Creates a healthy health.
     *
     * @return a health with UP status
     */
    public static Health up() {
        return Health.builder(Status.UP).withMessage(FUNCTIONAL).build();
    }

    /**
     * Creates a healthy health.
     *
     * @param message additional information on component's health
     * @return a health with UP status
     */
    public static Health up(String message) {
        return Health.builder(Status.UP).withMessage(message).build();
    }

    /**
     * Creates an unhealthy health.
     *
     * @param message the cause of why the component is down
     * @return a health with DOWN status
     */
    public static Health down(String message) {
        return Health.builder(Status.DOWN).withMessage(message).build();
    }

    /**
     * Creates an unhealthy health.
     *
     * @param e the cause of why the component is down
     * @return a health with DOWN status
     */
    public static Health down(Exception e) {
        return Health.builder(Status.DOWN).withMessage(e.toString()).build();
    }

    /**
     * Creates a struggling health.
     *
     * @param message the cause of why the component is down
     * @return a health with struggling status
     */
    public static Health struggling(String message) {
        return Health.builder(Status.STRUGGLING).withMessage(message).build();
    }

    /**
     * Creates a struggling health.
     *
     * @param e the cause of why the component is down
     * @return a health with struggling status
     */
    public static Health struggling(Exception e) {
        return Health.builder(Status.STRUGGLING).withMessage(e.toString()).build();
    }

    public static final class Builder {

        private final Status status;
        private String message;

        private Builder(Status status) {
            this.status = requireNonNull(status);
        }

        /**
         * Sets the {@code message} and returns a reference to this Builder
         * so that the methods can be chained together.
         *
         * @param message the {@code message} to set
         * @return a reference to this Builder
         */
        public Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public Health build() {
            return new Health(this);
        }

    }
}
