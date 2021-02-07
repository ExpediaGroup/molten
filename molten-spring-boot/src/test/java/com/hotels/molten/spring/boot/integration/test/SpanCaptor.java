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
package com.hotels.molten.spring.boot.integration.test;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

@Slf4j
public class SpanCaptor implements Reporter<Span> {
    private static final List<Span> CAPTURED_SPANS = Collections.synchronizedList(new ArrayList<>());

    public static void clearCapturedSpans() {
        LOG.info("Clearing captured spans.");
        CAPTURED_SPANS.clear();
    }

    public static List<Span> capturedSpans() {
        return List.copyOf(CAPTURED_SPANS);
    }

    public static void awaitForSpanWithName(String expectedName) {
        await().atMost(ofSeconds(3)).untilAsserted(() -> assertThat(capturedSpans())
            .anySatisfy(span -> assertThat(span).extracting(Span::name).isEqualTo(expectedName))
        );
    }

    @Override
    public void report(Span span) {
        LOG.info("Captured span {}", span);
        CAPTURED_SPANS.add(span);
    }
}
