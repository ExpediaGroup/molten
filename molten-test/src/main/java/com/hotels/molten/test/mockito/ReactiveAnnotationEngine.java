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

import static org.mockito.internal.exceptions.Reporter.moreThanOneAnnotationNotAllowed;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.configuration.CaptorAnnotationProcessor;
import org.mockito.internal.configuration.FieldAnnotationProcessor;
import org.mockito.internal.configuration.MockAnnotationProcessor;
import org.mockito.plugins.AnnotationEngine;

/**
 * Initializes fields annotated with &#64;{@link org.mockito.Mock} or &#64;{@link org.mockito.Captor}.
 * <p>
 * <p>
 * The {@link #process(Class, Object)} method implementation <strong>does not</strong> process super classes!
 *
 * @see org.mockito.MockitoAnnotations
 * @see org.mockito.internal.configuration.IndependentAnnotationEngine
 */
@SuppressWarnings("unchecked")
public class ReactiveAnnotationEngine implements AnnotationEngine {
    private final Map<Class<? extends Annotation>, FieldAnnotationProcessor<?>> annotationProcessorMap = new HashMap<Class<? extends Annotation>, FieldAnnotationProcessor<?>>();

    public ReactiveAnnotationEngine() {
        registerAnnotationProcessor(Mock.class, new MockAnnotationProcessor());
        registerAnnotationProcessor(Captor.class, new CaptorAnnotationProcessor());
        registerAnnotationProcessor(ReactiveMock.class, new ReactiveMockAnnotationProcessor());
    }

    @Override
    public void process(Class<?> clazz, Object testInstance) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            boolean alreadyAssigned = false;
            for (Annotation annotation : field.getAnnotations()) {
                Object mock = createMockFor(annotation, field);
                if (mock != null) {
                    throwIfAlreadyAssigned(field, alreadyAssigned);
                    alreadyAssigned = true;
                    try {
                        setField(testInstance, field, mock);
                    } catch (Exception e) {
                        throw new MockitoException("Problems setting field " + field.getName() + " annotated with " + annotation, e);
                    }
                }
            }
        }
    }

    private <A extends Annotation> void registerAnnotationProcessor(Class<A> annotationClass, FieldAnnotationProcessor<A> fieldAnnotationProcessor) {
        annotationProcessorMap.put(annotationClass, fieldAnnotationProcessor);
    }

    private Object createMockFor(Annotation annotation, Field field) {
        return forAnnotation(annotation).process(annotation, field);
    }

    private <A extends Annotation> FieldAnnotationProcessor<A> forAnnotation(A annotation) {
        return Optional.ofNullable((FieldAnnotationProcessor<A>) annotationProcessorMap.get(annotation.annotationType())).orElse((annotation1, field) -> null);
    }

    private void throwIfAlreadyAssigned(Field field, boolean alreadyAssigned) {
        if (alreadyAssigned) {
            throw moreThanOneAnnotationNotAllowed(field.getName());
        }
    }
}
