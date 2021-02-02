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

import org.mockito.configuration.AnnotationEngine;
import org.mockito.configuration.DefaultMockitoConfiguration;
import org.mockito.internal.configuration.plugins.Plugins;
import org.mockito.stubbing.Answer;

/**
 * Mockito configuration to set the default answer. To configure mockito further, well you just can't do that right now.
 */
public class ReactiveMockitoConfiguration extends DefaultMockitoConfiguration {
    @Override
    public Answer<Object> getDefaultAnswer() {
        return new ReactiveAnswer(super.getDefaultAnswer());
    }

    /**
     * Legacy way to provide annotation engine from {@link Plugins#getAnnotationEngine()}, which is the default behaviour.
     *
     * @see org.mockito.configuration.IMockitoConfiguration#getAnnotationEngine()
     * @deprecated will be removed once removed from {@link org.mockito.configuration.IMockitoConfiguration} by the Mockito team
     */
    @Deprecated
    @Override
    public AnnotationEngine getAnnotationEngine() {
        return Plugins.getAnnotationEngine()::process;
    }
}
