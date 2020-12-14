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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import org.mockito.internal.stubbing.defaultanswers.ReturnsEmptyValues;
import org.mockito.invocation.InvocationOnMock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Mockito default return values for Reactor reactive types.
 */
public class ReactiveAnswer extends ReturnsEmptyValues {

    @Override
    public Object answer(InvocationOnMock invocation) {
        Object answer = super.answer(invocation);
        if (answer == null) {
            Class<?> returnType = invocation.getMethod().getReturnType();
            if (returnType == Mono.class) {
                answer = Mono.empty();
            } else if (returnType == Flux.class) {
                answer = Flux.empty();
            }
        }
        return answer;
    }

    /**
     * Creates a mock with default reactive answers.
     * Also honors default methods invoking them as is.
     *
     * @param classToMock the type to create mock for
     * @param <T>         the actual type of the mock
     * @return the mock
     */
    public static <T> T reactiveMock(Class<T> classToMock) {
        return mock(classToMock,
            withSettings().defaultAnswer(invocation -> invocation.getMethod().isDefault() ? invocation.callRealMethod() : new ReactiveAnswer().answer(invocation)));
    }
}
