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
package com.hotels.molten.spring.boot;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import com.hotels.molten.core.MoltenCore;
import com.hotels.molten.core.mdc.MoltenMDC;
import com.hotels.molten.core.metrics.MoltenMetrics;

/**
 * Initializes Molten for spring-boot.
 * Besides core and MDC initialization it also enables dimensional metrics.
 * Registers MDC propagation for each operator.
 */
public class MoltenCoreSpringBootInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        MoltenCore.initialize();
        MoltenMDC.initialize(true);
        MoltenMetrics.setDimensionalMetricsEnabled(true);
    }
}

