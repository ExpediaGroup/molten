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

package com.hotels.molten.http.client.listener;

import static com.hotels.molten.core.metrics.MetricsSupport.name;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import lombok.Value;
import okhttp3.HttpUrl;

import com.hotels.molten.core.metrics.MetricId;

/**
 * Reports all http event metrics as durations under {@code [qualifier].duration.[eventId]}.
 */
@Value
public class DurationMetricsReporterHandler implements HttpCallMetricsHandler {
    private static final Comparator<Long> LONG_COMPARATOR = Comparator.naturalOrder();
    @NonNull
    private final MeterRegistry meterRegistry;
    @NonNull
    private final String clientId;

    @Override
    public void handleHttpCallMetrics(HttpUrl httpUrl, Map<HttpEvent, Collection<Long>> httpCallMetrics) {
        httpCallMetrics.forEach((event, eventTimes) -> event.getFromEvent()
            .map(httpCallMetrics::get)
            .flatMap(fromTimes -> fromTimes.stream().min(LONG_COMPARATOR)
                .flatMap(fromTime -> eventTimes.stream().max(LONG_COMPARATOR)
                    .map(toTime -> toTime - fromTime)
                )
            ).ifPresent(durationMs -> registerEvent(event.getDurationId().orElse(event.getId()), durationMs)));
    }

    private void registerEvent(String event, long durationMs) {
        MetricId.builder()
            .name("http_client_request_trace_duration")
            .hierarchicalName(hierarchicalName(event))
            .tag(Tag.of("client", clientId))
            .tag(Tag.of("event", event))
            .build()
            .toTimer()
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    private String hierarchicalName(String event) {
        return name("client", clientId, "http-trace", "duration", event);
    }
}
