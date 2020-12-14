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

package com.hotels.molten.healthcheck.snapshot;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import com.hotels.molten.healthcheck.HealthAssert;

/**
 * Assertion class for {@link HealthSnapshot}.
 */
public class HealthSnapshotAssert extends AbstractAssert<HealthSnapshotAssert, HealthSnapshot> {

    public HealthSnapshotAssert(HealthSnapshot healthSnapshot) {
        super(healthSnapshot, HealthSnapshotAssert.class);
    }

    /**
     * Creates assertion.
     *
     * @param snapshot to validate
     * @return the assertion
     */
    public static HealthSnapshotAssert assertThat(HealthSnapshot snapshot) {
        return new HealthSnapshotAssert(snapshot);
    }

    /**
     * Checks similarity. It omits the timestamp in the {@link com.hotels.molten.healthcheck.Health} objects.
     *
     * @param healthSnapshot the expected healthSnapshot
     * @return this assertion
     */
    public HealthSnapshotAssert isSimilarTo(HealthSnapshot healthSnapshot) {
        isNotNull();
        hasName(healthSnapshot.getName());
        HealthAssert.assertThat(actual.getHealth()).isSameExceptTimestamp(healthSnapshot.getHealth());
        Assertions.assertThat(actual.getComponents())
            .allSatisfy(component -> Assertions.assertThat(healthSnapshot.getComponents())
                .anySatisfy(referenceComponent -> assertThat(component).isSimilarTo(referenceComponent)));
        return this;
    }

    /**
     * Checks the name.
     *
     * @param name the expected name
     * @return this assertion
     */
    public HealthSnapshotAssert hasName(String name) {
        isNotNull();
        Assertions.assertThat(actual.getName()).isEqualTo(name);
        return this;
    }
}
