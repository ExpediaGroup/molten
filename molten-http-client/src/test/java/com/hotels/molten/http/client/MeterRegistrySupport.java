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

package com.hotels.molten.http.client;

import java.util.Map;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MeterRegistrySupport {

    /**
     * Provides a new {@link SimpleMeterRegistry}.
     *
     * @return the meter registry
     */
    public static MeterRegistry simpleRegistry() {
        return new SimpleMeterRegistry();
    }

    /**
     * Provides a shared meter registry with graphite reporter prefixing metrics with {@code molten.local.default.} qualifier.
     *
     * @return the meter registry
     */
    public static MeterRegistry localGraphiteRegistry() {
        return LocalGraphiteMeterRegistry.INSTANCE.meterRegistry;
    }

    private enum LocalGraphiteMeterRegistry {
        INSTANCE {
            MeterRegistry create() {
                LOG.warn("Creating meter registry with Graphite reporter");
                GraphiteConfig graphiteConfig = new GraphiteConfig() {
                    private final Map<String, String> config = Map.of(
                        "graphite.enabled", "true",
                        "graphite.host", "localhost",
                        "graphite.port", "2004",
                        "graphite.step", "PT1s"
                    );

                    @Override
                    public String get(String key) {
                        return config.get(key);
                    }

                    @Override
                    public String[] tagsAsPrefix() {
                        return new String[] {"application", "environment", "instance"};
                    }
                };
                GraphiteMeterRegistry meterRegistry = new GraphiteMeterRegistry(graphiteConfig, Clock.SYSTEM);
                meterRegistry.config().namingConvention(NamingConvention.dot);
                meterRegistry.config().commonTags("application", "molten", "environment", "local", "instance", "default");
                return meterRegistry;
            }
        };

        private final MeterRegistry meterRegistry;

        LocalGraphiteMeterRegistry() {
            this.meterRegistry = create();
        }

        abstract MeterRegistry create();
    }
}
