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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;

import java.util.function.Consumer;

import org.hamcrest.Matcher;

/**
 * Support class to simplify testing reactive APIs.
 */
public class ReactiveTestSupport {

    /**
     * Creates a predicate which asserts that an exception is of a given type and with expected cause.
     *
     * @param exceptedType  the expected type
     * @param expectedCause matcher for the expected cause
     * @return the predicate
     */
    public static Consumer<Throwable> anErrorWith(Class<? extends Throwable> exceptedType, Matcher<? extends Exception> expectedCause) {
        return ex -> {
            assertThat(ex, both(instanceOf(exceptedType)).and(hasProperty("cause", expectedCause)));
        };
    }
}
