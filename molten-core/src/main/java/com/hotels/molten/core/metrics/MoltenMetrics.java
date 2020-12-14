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

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Generic metrics support in Molten. The default metric names are hierarchical.
 * <p>
 * Reporting of dimensional metrics are also supported via {@link #setDimensionalMetricsEnabled(boolean)}.
 * <p>
 * To simplify reporting to both hierarchical and dimensional metrics (e.g. while migrating from hierarchical to dimensional),
 * one can enable reporting hierarchical name as well in {@code graphite-id} label with {@link #setGraphiteIdMetricsLabelEnabled(boolean)}.
 * <br/>
 * To keep hierarchical names in e.g. {@code io.micrometer.graphite.GraphiteMeterRegistry},
 * a custom {@link io.micrometer.core.instrument.util.HierarchicalNameMapper} can be defined
 * which prefers this label as the full metric name when available.
 * To avoid spamming dimensional metrics with this label, one can add a meter filter to exclude this tag from reporting:
 * {@code MeterFilter.ignoreTags("graphite-id")}.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class MoltenMetrics {
    private static boolean dimensionalMetricsEnabled = Boolean.valueOf(System.getProperty("MOLTEN_METRICS_DIMENSIONAL_METRICS_ENABLED", "false"));
    private static boolean graphiteIdMetricsLabelEnabled = Boolean.valueOf(System.getProperty("MOLTEN_METRICS_GRAPHITE_ID_LABEL_ADDED", "false"));

    /**
     * When enabled metrics will be dimensional. i.e. short, common names with specific labels.
     * When disabled metric names will be hierarchical. i.e. specific hierarchical names separated with dots.
     *
     * @param dimensionalMetricsEnabled whether to enable dimensional metrics
     */
    public static void setDimensionalMetricsEnabled(boolean dimensionalMetricsEnabled) {
        MoltenMetrics.dimensionalMetricsEnabled = dimensionalMetricsEnabled;
    }

    /**
     * Returns true if dimensional metrics are enabled. Should not be used directly.
     * See {@link MetricId} for a simpler metric creation.
     *
     * @return true if dimensional metrics are enabled, otherwise false
     */
    static boolean isDimensionalMetricsEnabled() {
        return dimensionalMetricsEnabled;
    }

    /**
     * When enabled and dimensional metrics are also enabled then a {@code graphite-id} label
     * will be added with a specific hierarchical name for each metric.
     *
     * @param graphiteIdMetricsLabelEnabled whether to add hierarchical metric name under {@code graphite-id} label
     */
    public static void setGraphiteIdMetricsLabelEnabled(boolean graphiteIdMetricsLabelEnabled) {
        MoltenMetrics.graphiteIdMetricsLabelEnabled = graphiteIdMetricsLabelEnabled;
    }

    /**
     * Returns true if graphite ID as label is enabled. Should not be used directly.
     * See {@link MetricId} for a simpler metric creation.
     *
     * @return true if {@code graphite-id} label is enabled, otherwise false
     */
    static boolean isGraphiteIdMetricsLabelEnabled() {
        return graphiteIdMetricsLabelEnabled;
    }
}
