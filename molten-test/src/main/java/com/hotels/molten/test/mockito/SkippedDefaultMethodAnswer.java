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

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.mockito.Answers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Mockito {@link Answer} to honor default methods invoking them as is.
 * Other invocations are delegated to the given {@link Answer}.
 *
 * @see Answers
 * @deprecated To keep non-abstract methods on a mock, please configure it's default answer with {@link Answers#CALLS_REAL_METHODS} instead.
 * <p>
 * To keep implementation on specific method, use the usual mocking mechanism. Example: {@code when(mock.someMethod()).thenAnswer(CALLS_REAL_METHODS)}.
 */
@Deprecated
@RequiredArgsConstructor
public class SkippedDefaultMethodAnswer implements Answer<Object> {
    @NonNull
    private final Answer<Object> delegate;

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        return invocation.getMethod().isDefault()
            ? Answers.CALLS_REAL_METHODS.answer(invocation)
            : delegate.answer(invocation);
    }
}
