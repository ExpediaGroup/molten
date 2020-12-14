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

import org.mockito.exceptions.base.MockitoException;

/**
 * Initializes regular Mockito mocks and reactive mocks.
 */
public class ReactiveMockitoAnnotations {
    /**
     * Initializes objects annotated with Mockito annotations for given testClass:
     * &#064;{@link org.mockito.Mock}, &#064;{@link org.mockito.Spy}, &#064;{@link org.mockito.Captor}, &#064;{@link org.mockito.InjectMocks}
     * <p>
     * See examples in javadoc for {@link org.mockito.MockitoAnnotations} class.
     */
    public static void initMocks(Object testClass) {
        if (testClass == null) {
            throw new MockitoException("testClass cannot be null. For info how to use @Mock annotations see examples in javadoc for MockitoAnnotations class");
        }
        new ReactiveAnnotationEngine().process(testClass.getClass(), testClass);
    }
}
