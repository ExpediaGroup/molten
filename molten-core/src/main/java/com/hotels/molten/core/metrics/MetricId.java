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

import static com.hotels.molten.core.metrics.MetricsSupport.name;
import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

/**
 * A metric identifier supporting both dimensional and hierarchical metrics.
 * <p>
 * Depending on current global configuration components relying on this class will register dimensional, hierarchical, or both metrics.
 * See {@link MoltenMetrics#setDimensionalMetricsEnabled(boolean)} and {@link MoltenMetrics#setGraphiteIdMetricsLabelEnabled(boolean)} for
 * more information.
 */
@Value
@Builder(toBuilder = true)
public class MetricId {
    @NonNull
    private final String name;
    @Nullable
    private final String hierarchicalName;
    @NonNull
    @Singular
    private final List<Tag> tags;

    /**
     * Gets the tags associated with this metric ID.
     *
     * @return the list of tags
     */
    public Iterable<Tag> getTags() {
        return !MoltenMetrics.isGraphiteIdMetricsLabelEnabled()
            ? tags
            : MetricsSupport.tagsWithId(safeHierarchicalName(), tags.toArray(new Tag[0]));
    }

    /**
     * Alters the current metric ID by adding postfix to its name.
     * It also appends this postfix to the hierarchical name if it's present but replaces every {@code _} with {@code .} in it.
     *
     * @param postfix the postfix to add to the name
     * @return the altered metric ID
     */
    public MetricId extendWith(String postfix) {
        requireNonNull(postfix);
        return new MetricId(name + "_" + postfix, hierarchicalName != null ? name(hierarchicalName, postfix.replaceAll("_+", ".")) : null, tags);
    }

    /**
     * Alters the current metric ID by adding postfix to its name.
     *
     * @param namePostfix the postfix to add to the name, concatenated with {@code _}
     * @param hierarchicalNamePostfix the postfix to add to the hierarchical name, concatenated with {@code .}
     * @return the altered metric ID
     */
    public MetricId extendWith(String namePostfix, String hierarchicalNamePostfix) {
        requireNonNull(namePostfix);
        requireNonNull(hierarchicalName, "The hierarchical name was not set");
        requireNonNull(hierarchicalNamePostfix);
        return new MetricId(name + "_" + namePostfix, name(hierarchicalName, hierarchicalNamePostfix), tags);
    }

    /**
     * Extends the current metric ID with a tag.
     * The tag value will be appended to the hierarchical name if it's present.
     *
     * @param tag the tag to add
     * @return the extended metric ID
     */
    public MetricId tagWith(Tag tag) {
        return toBuilder().tag(tag).hierarchicalName(hierarchicalName != null ? name(hierarchicalName, tag.getValue()) : null).build();
    }

    private String safeHierarchicalName() {
        return hierarchicalName != null ? hierarchicalName : name;
    }

    /**
     * Creates a {@link Timer} builder based on this metric ID.
     *
     * @return the builder
     */
    public Timer.Builder toTimer() {
        Timer.Builder timer;
        if (MoltenMetrics.isDimensionalMetricsEnabled()) {
            timer = Timer.builder(name).tags(getTags());
        } else {
            timer = Timer.builder(safeHierarchicalName());
        }
        return timer;
    }

    /**
     * Creates a {@link Counter} builder based on this metric ID.
     *
     * @return the builder
     */
    public Counter.Builder toCounter() {
        Counter.Builder timer;
        if (MoltenMetrics.isDimensionalMetricsEnabled()) {
            timer = Counter.builder(name).tags(getTags());
        } else {
            timer = Counter.builder(safeHierarchicalName());
        }
        return timer;
    }

    /**
     * Creates a {@link Gauge} builder based on this metric ID.
     *
     * @return the builder
     */
    public <T> Gauge.Builder<T> toGauge(T target, ToDoubleFunction<T> toValue) {
        Gauge.Builder<T> gauge;
        if (MoltenMetrics.isDimensionalMetricsEnabled()) {
            gauge = Gauge.builder(name, target, toValue).tags(getTags());
        } else {
            gauge = Gauge.builder(safeHierarchicalName(), target, toValue);
        }
        return gauge;
    }

    /**
     * Creates a {@link DistributionSummary} builder based on this metric ID.
     *
     * @return the builder
     */
    public DistributionSummary.Builder toDistributionSummary() {
        DistributionSummary.Builder distributionSummary;
        if (MoltenMetrics.isDimensionalMetricsEnabled()) {
            distributionSummary = DistributionSummary.builder(name).tags(getTags());
        } else {
            distributionSummary = DistributionSummary.builder(safeHierarchicalName());
        }
        return distributionSummary;
    }
}
