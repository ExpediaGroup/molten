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

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.Value;
import okhttp3.HttpUrl;
import org.slf4j.Logger;

/**
 * Handler implementation for logging event's metrics of a retrofit call.
 */
@Value
public class LoggingHttpCallMetricsHandler implements HttpCallMetricsHandler {
    @NonNull
    private final Logger logger;

    @Override
    public void handleHttpCallMetrics(HttpUrl httpUrl, Map<HttpEvent, Collection<Long>> httpCallMetrics) {
        String eventItemsLog = httpCallMetrics.entrySet()
            .stream()
            .flatMap(entry -> entry.getValue().stream().map(value -> entry.getKey().getId() + "=" + value))
            .sorted()
            .collect(Collectors.joining(" "));
        logger.info(httpUrl.uri() + " httpEvents " + eventItemsLog);
    }
}
