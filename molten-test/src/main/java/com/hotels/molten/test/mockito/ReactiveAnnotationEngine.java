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

import static java.util.stream.Collectors.toSet;
import static org.mockito.internal.exceptions.Reporter.unsupportedCombinationOfAnnotations;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Stream;

import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.configuration.FieldAnnotationProcessor;

/**
 * Initializes fields annotated with &#64;{@link ReactiveMock}.
 * <p>
 * The {@link #process(Class, Object)} method implementation <strong>does not</strong> process super classes!
 *
 * @see ReactiveInjectingAnnotationEngine
 * @see org.mockito.internal.configuration.InjectingAnnotationEngine
 */
public class ReactiveAnnotationEngine {
    private final FieldAnnotationProcessor<ReactiveMock> reactiveMockProcessor = new ReactiveMockAnnotationProcessor();

    // not extends AnnotationEngine so can return the created mocks
    public Set<Object> process(Class<?> clazz, Object testInstance) {
        return Stream.of(clazz.getDeclaredFields())
            .filter(field -> field.isAnnotationPresent(ReactiveMock.class))
            .peek(field -> assertNoIncompatibleAnnotations(ReactiveMock.class, field, Mock.class, Spy.class, Captor.class, InjectMocks.class))
            .map(field -> {
                Object reactiveMock = reactiveMockProcessor.process(field.getAnnotation(ReactiveMock.class), field);
                try {
                    setField(Modifier.isStatic(field.getModifiers()) ? null : testInstance, field, reactiveMock);
                } catch (Exception e) {
                    throw new MockitoException("Problems setting field " + field.getName() + " annotated with " + ReactiveMock.class, e);
                }
                return reactiveMock;
            })
            .collect(toSet());
    }

    @SafeVarargs
    private static void assertNoIncompatibleAnnotations(
        Class<? extends Annotation> annotation,
        Field field,
        Class<? extends Annotation>... undesiredAnnotations) {
        for (Class<? extends Annotation> u : undesiredAnnotations) {
            if (field.isAnnotationPresent(u)) {
                throw unsupportedCombinationOfAnnotations(
                    annotation.getSimpleName(), u.getSimpleName());
            }
        }
    }
}
