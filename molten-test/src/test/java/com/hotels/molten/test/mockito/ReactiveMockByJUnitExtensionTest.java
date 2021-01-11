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
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Unit test for {@link ReactiveMock} initiated by {@link MockitoExtension}.
 */
@ExtendWith(MockitoExtension.class)
public class ReactiveMockByJUnitExtensionTest {
    // TODO reactive mock parameter
    // TODO inject reactive mock should work
    // TODO already a mock?
    // TODO support answers
    // TODO support lenient
    private static final int ID = 1;
    private static final String VALUE_A = "a";
    private static final String VALUE_B = "b";
    @ReactiveMock
    private ReactiveApi reactiveApi;
    @ReactiveMock(serializable = true, stubOnly = true, extraInterfaces = Function.class, name = "custom name")
    private ReactiveApi serializableStubOnlyReactiveApi;
    @ReactiveMock(answer = Answers.RETURNS_DEEP_STUBS)
    private ReactiveApi deepReactiveApi;

    @Test
    public void shouldEmitStubValue() {
        when(reactiveApi.getAll(ID)).thenReturn(Flux.just(VALUE_A, VALUE_B));

        StepVerifier.create(reactiveApi.getAll(ID)).expectSubscription().expectNext(VALUE_A, VALUE_B).expectComplete().verify();
    }

    @Test
    public void shouldEmitEmptyByDefault() {
        StepVerifier.create(reactiveApi.getAll(ID)).expectSubscription().expectComplete().verify();
    }

    @Test
    public void shouldSupportDefaultMethod() {
        when(reactiveApi.getAll(ID)).thenReturn(Flux.just(VALUE_A, VALUE_B));

        StepVerifier.create(reactiveApi.getFirst(ID)).expectSubscription().expectNext(VALUE_A).expectComplete().verify();
    }

    @Test
    public void shouldSupportMockitoAnnotationProperties() {
        assertThat(serializableStubOnlyReactiveApi, is(not(nullValue())));
    }

    @Test
    public void shouldSupportCustomAnswer() throws Exception {
        when(deepReactiveApi.self().self()).thenReturn(deepReactiveApi);

        assertThat(deepReactiveApi.self().self(), sameInstance(deepReactiveApi));
        assertThat(deepReactiveApi.self().self().getAll(ID), is(not(nullValue())));

        StepVerifier.create(deepReactiveApi.getAll(ID)).expectSubscription().expectComplete().verify(Duration.ofSeconds(2L));
        StepVerifier.create(deepReactiveApi.getAll(ID)).expectSubscription().expectComplete().verify(Duration.ofSeconds(2L));
    }
}
