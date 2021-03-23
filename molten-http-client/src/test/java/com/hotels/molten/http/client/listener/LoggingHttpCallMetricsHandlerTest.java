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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import ch.qos.logback.classic.Logger;
import com.google.common.collect.ImmutableMultimap;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test for {@link LoggingHttpCallMetricsHandler}.
 */
@ExtendWith(MockitoExtension.class)
public class LoggingHttpCallMetricsHandlerTest {

    @Mock
    private HttpUrl requestUrl;
    @Mock
    private Logger logger;

    @Test
    public void should_create_proper_event_log() {
        // Given
        var events = ImmutableMultimap.<HttpEvent, Long>builder()
            .put(HttpEvent.CALL_START, 0L)
            .put(HttpEvent.DNS_START, 100L)
            .put(HttpEvent.DNS_START, 150L)
            .put(HttpEvent.CANCELED, 200L)
            .put(HttpEvent.CALL_FAILED, 300L)
            .build();
        when(requestUrl.uri()).thenReturn(URI.create("http://test-molten/"));
        LoggingHttpCallMetricsHandler loggingHttpCallMetricsHandler = new LoggingHttpCallMetricsHandler(logger);
        // When
        loggingHttpCallMetricsHandler.handleHttpCallMetrics(requestUrl, events.asMap());
        // Then
        var logMessage = ArgumentCaptor.forClass(String.class);
        verify(logger, times(1)).info(logMessage.capture());
        assertThat(logMessage.getValue()).contains("http://test-molten/", "callStart=0", "dnsStart=100", "dnsStart=150", "canceled=200", "callFailed=300");
    }
}
