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

package org.mockito.configuration;

import org.mockito.exceptions.misusing.MockitoConfigurationException;
import org.mockito.stubbing.Answer;

/**
 * Mockito configuration to set {@code com.hotels.molten.test.mockito.ReactiveMockitoConfiguration} as default.
 * <p>
 * In order to configure Mockito on your own, just exclude {@code com.expediagroup.molten:molten-test-mockito-autoconfigure} transitive dependency
 * from your {@code com.expediagroup.molten:molten-test} dependency.
 */
public class MockitoConfiguration extends DefaultMockitoConfiguration {
    private static final String REACTIVE_CONFIGURATION_CLASS_NAME = "com.hotels.molten.test.mockito.ReactiveMockitoConfiguration";
    private static final IMockitoConfiguration REACTIVE_CONFIGURATION;

    static {
        Class<?> configClass;
        try {
            configClass = Class.forName(REACTIVE_CONFIGURATION_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            throw new MockitoConfigurationException("Unable to find " + REACTIVE_CONFIGURATION_CLASS_NAME + " class."
                + "Is there the correct version of 'com.expediagroup.molten:molten-test' on the classpath?", e);
        }
        try {
            REACTIVE_CONFIGURATION = (IMockitoConfiguration) configClass.getDeclaredConstructor().newInstance();
        } catch (ClassCastException e) {
            throw new MockitoConfigurationException("MockitoConfiguration class must implement " + IMockitoConfiguration.class.getName() + " interface.", e);
        } catch (Exception e) {
            throw new MockitoConfigurationException("Unable to instantiate " + REACTIVE_CONFIGURATION_CLASS_NAME + " class."
                + "Is there the correct version of 'com.expediagroup.molten:molten-test' on the classpath?", e);
        }
    }

    @Override
    public Answer<Object> getDefaultAnswer() {
        return REACTIVE_CONFIGURATION.getDefaultAnswer();
    }

    @Override
    public AnnotationEngine getAnnotationEngine() {
        return REACTIVE_CONFIGURATION.getAnnotationEngine();
    }
}
