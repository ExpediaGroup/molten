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
 * <strong><code>ReactiveMockitoAnnotations.initMocks(this)</code></strong> method has to be called to initialize annotated objects.
 *
 * @see org.mockito.Spy
 * @see org.mockito.InjectMocks
 * @see ReactiveMockitoAnnotations#initMocks(Object)
 * @see org.mockito.junit.MockitoJUnitRunner
 */
@Target(FIELD)
@Retention(RUNTIME)
@Documented
public @interface ReactiveMock {
    String name() default "";

    Class<?>[] extraInterfaces() default {};

    boolean stubOnly() default false;

    boolean serializable() default false;
}
