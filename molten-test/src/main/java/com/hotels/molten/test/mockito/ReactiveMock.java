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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a field as a reactive mock.
 * <p>
 * <strong>{@link ReactiveMockitoAnnotations#initMocks(Object)}</strong> method has to be called to initialize annotated objects.
 *
 * @see org.mockito.Mock
 * @see org.mockito.Spy
 * @see org.mockito.InjectMocks
 * @see org.mockito.MockitoAnnotations#openMocks(Object)
 * @see org.mockito.junit.MockitoJUnitRunner
 * @deprecated Please use the usual way to create your reactive mocks, like annotation with {@link org.mockito.Mock} or creating with {@link org.mockito.Mockito#mock(Class)}.
 * <p>
 * To skip stubbing non-abstract methods on a mock, please configure it's default answer with {@link org.mockito.Answers#CALLS_REAL_METHODS}.
 * <p>
 * To skip stubbing a specific non-abstract method, use the usual mocking mechanisms, like {@code when(mock.someMethod()).thenCallRealMethod()}.
 */
@Target(FIELD)
@Retention(RUNTIME)
@Documented
@Deprecated
public @interface ReactiveMock {

    /**
     * Mock will have custom name (shown in verification errors), see {@link org.mockito.MockSettings#name(String)}.
     */
    String name() default "";

    /**
     * Mock will have extra interfaces, see {@link org.mockito.MockSettings#extraInterfaces(Class[])}.
     */
    Class<?>[] extraInterfaces() default {};

    /**
     * Mock will be 'stubOnly', see {@link org.mockito.MockSettings#stubOnly()}.
     */
    boolean stubOnly() default false;

    /**
     * Mock will be serializable, see {@link org.mockito.MockSettings#serializable()}.
     */
    boolean serializable() default false;

    /**
     * Mock will be lenient, see {@link org.mockito.MockSettings#lenient()}.
     */
    boolean lenient() default false;
}
