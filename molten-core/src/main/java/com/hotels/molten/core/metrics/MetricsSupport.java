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

package com.hotels.molten.core.metrics;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Support methods for metrics handling.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class MetricsSupport {
    public static final String GRAPHITE_ID = "graphite-id";

    /**
     * Constructs a hierarchical style metric name from parameters. Omits empty or null parts.
     *
     * @param name  the first mandatory part
     * @param names the other optional parts
     * @return the metric name
     */
    public static String name(String name, String... names) {
        final StringBuilder builder = new StringBuilder();
        append(builder, name);
        if (names != null) {
            for (String s : names) {
                append(builder, s);
            }
        }
        return builder.toString();
    }

    /**
     * Creates a list of tags with a {@code graphite-id} tag for the hierarchical name
     * if {@link MoltenMetrics#isGraphiteIdMetricsLabelEnabled()} is enabled.
     *
     * @param hierarchicalName the hierarchical name to add
     * @param tags the tags to add
     * @return the tag list
     */
    public static Iterable<Tag> tagsWithId(String hierarchicalName, Tag... tags) {
        Tags allTags = Tags.of(tags);
        if (MoltenMetrics.isGraphiteIdMetricsLabelEnabled()) {
            allTags = allTags.and(Tag.of(GRAPHITE_ID, hierarchicalName));
        }
        return allTags;
    }

    private static void append(StringBuilder builder, String part) {
        if (part != null && !part.isEmpty()) {
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(part);
        }
    }
}
