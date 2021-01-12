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

package com.hotels.molten.test.mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ReactiveMockTestCases {
    private static final int ID = 1;
    private static final String VALUE_A = "a";
    private static final String VALUE_B = "b";

    static void shouldEmitStubValue(ReactiveApi reactiveApi) {
        when(reactiveApi.getAll(ID)).thenReturn(Flux.just(VALUE_A, VALUE_B));

        StepVerifier.create(reactiveApi.getAll(ID)).expectSubscription().expectNext(VALUE_A, VALUE_B).expectComplete().verify();
    }

    static void shouldEmitEmptyByDefault(ReactiveApi reactiveApi) {
        StepVerifier.create(reactiveApi.getAll(ID)).expectSubscription().expectComplete().verify();
    }

    static void shouldSupportDefaultMethod(ReactiveApi reactiveApi) {
        when(reactiveApi.getAll(ID)).thenReturn(Flux.just(VALUE_A, VALUE_B));

        StepVerifier.create(reactiveApi.getFirst(ID)).expectSubscription().expectNext(VALUE_A).expectComplete().verify();
    }

    static void shouldSupportMockitoAnnotationProperties(ReactiveApi reactiveApi) {
        assertThat(reactiveApi, is(not(nullValue())));
    }
}
