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

import javax.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.autoconfig.instrument.reactor.TraceReactorAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hotels.molten.spring.web.SleuthMoltenBridgeWebFilter;
import com.hotels.molten.trace.MoltenTrace;

@Configuration
@ConditionalOnProperty(value = "spring.sleuth.reactor.enabled", matchIfMissing = true)
@AutoConfigureAfter(value = TraceReactorAutoConfiguration.class)
public class MoltenTraceSpringBootAutoConfiguration {

    @PostConstruct
    public void initTracing() {
        MoltenTrace.initialize(true);
    }

    @Bean
    public SleuthMoltenBridgeWebFilter tracePropagatorWebFilter() {
        return new SleuthMoltenBridgeWebFilter();
    }
}
