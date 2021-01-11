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

import org.mockito.Answers;

/**
 * Marks a field as a reactive mock.
 * <p>
 * <strong><code>ReactiveMockitoAnnotations.initMocks(this)</code></strong> method has to be called to initialize annotated objects.
 *
 * @see org.mockito.Mock
 * @see org.mockito.Spy
 * @see org.mockito.InjectMocks
 * @see org.mockito.MockitoAnnotations#openMocks(Object)
 * @see org.mockito.junit.MockitoJUnitRunner
 */
@Target(FIELD)
@Retention(RUNTIME)
@Documented
public @interface ReactiveMock {

    /**
     * Sets the default answer for methods with <b>non-reactive</b> return types, except the interface default methods.
     * <p>
     * For reactive return types, default reactive answers are applied regardless of this.
     *
     * @see ReactiveAnswer
     * @see org.mockito.MockSettings#defaultAnswer
     */
    Answers answer() default Answers.RETURNS_DEFAULTS;

    /**
     * Enables mocking of default methods of interfaces.
     *
     * @see org.mockito.MockSettings#defaultAnswer
     */
    boolean mockDefaultMethods() default false;

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
