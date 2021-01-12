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

import java.util.Map;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Mockito answer to return default values for Reactor reactive types.
 */
@RequiredArgsConstructor
public class ReactiveAnswer implements Answer<Object> {
    private final Map<Class<?>, Object> reactiveDefaultAnswers = Map.of(
        Mono.class, Mono.empty(),
        Flux.class, Flux.empty()
    );
    @NonNull
    private final Answer<Object> delegateAnswer;

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        Class<?> returnType = invocation.getMethod().getReturnType();
        Object answer = reactiveDefaultAnswers.get(returnType);
        if (answer == null) {
            answer = delegateAnswer.answer(invocation);
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
     * @deprecated will be removed, use {@link org.mockito.Mockito#mock(Class)} instead
     */
    @Deprecated
    public static <T> T reactiveMock(Class<T> classToMock) {
        return mock(classToMock);
    }
}
