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

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

/**
 * AssertJ assert class for {@link Health}.
 */
public class HealthAssert extends AbstractAssert<HealthAssert, Health> {

    public HealthAssert(Health health) {
        super(health, HealthAssert.class);
    }

    /**
     * Creates assertion.
     *
     * @param health to validate
     * @return the assertion
     */
    public static HealthAssert assertThat(Health health) {
        return new HealthAssert(health);
    }

    /**
     * Checks the status.
     *
     * @param status the expected status
     * @return this assertion
     */
    public HealthAssert hasStatus(Status status) {
        isNotNull();
        Assertions.assertThat(actual.status()).isSameAs(status);
        return this;
    }

    /**
     * Checks the message.
     *
     * @param message the expected message
     * @return this assertion
     */
    public HealthAssert hasMessage(String message) {
        isNotNull();
        Assertions.assertThat(actual.message()).hasValue(message);
        return this;
    }

    /**
     * Checks if the status and the message are the same. It omits the timestamp.
     *
     * @param health the expected health
     * @return this assertion
     */
    public HealthAssert isSameExceptTimestamp(Health health) {
        isNotNull();
        hasStatus(health.status());
        health.message().ifPresent(this::hasMessage);
        return this;
    }
}
