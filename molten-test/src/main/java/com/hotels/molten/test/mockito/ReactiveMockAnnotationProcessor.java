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

import java.lang.reflect.Field;

import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.mockito.internal.configuration.FieldAnnotationProcessor;
import org.mockito.stubbing.Answer;

/**
 * Instantiates a mock on a field annotated by {@link ReactiveMock}.
 */
public class ReactiveMockAnnotationProcessor implements FieldAnnotationProcessor<ReactiveMock> {
    public Object process(ReactiveMock annotation, Field field) {
        return mock(field.getType(), createSettings(annotation, field.getName())); // TODO add scoped mock support
    }

    private MockSettings createSettings(ReactiveMock annotation, String defaultName) {
        MockSettings mockSettings = Mockito.withSettings();
        if (annotation.extraInterfaces().length > 0) {
            mockSettings.extraInterfaces(annotation.extraInterfaces());
        }
        if ("".equals(annotation.name())) {
            mockSettings.name(defaultName);
        } else {
            mockSettings.name(annotation.name());
        }
        if (annotation.serializable()) {
            mockSettings.serializable();
        }
        if (annotation.stubOnly()) {
            mockSettings.stubOnly();
        }
        if (annotation.lenient()) {
            mockSettings.lenient();
        }
        return mockSettings.defaultAnswer(createAnswer(annotation));
    }

    private Answer<Object> createAnswer(ReactiveMock annotation) {
        return annotation.mockDefaultMethods()
            ? new ReactiveAnswer(annotation.answer())
            : new SkippedDefaultMethodAnswer(new ReactiveAnswer(annotation.answer()));
    }
}

