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

import java.util.Optional;
import javax.inject.Inject;

import brave.http.HttpTracing;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import com.hotels.molten.http.client.RetrofitServiceClientBuilder;
import com.hotels.molten.spring.boot.integration.SpringBootWebFluxIntegrationTest;

@Configuration
public class WebConfiguration {

    @Value("${MOCK_SERVER_URL:}")
    private String mockServerUrl;

    @Inject
    private MeterRegistry meterRegistry;
    @Inject
    private HttpTracing httpTracing;

    @Bean
    public Reporter<Span> capturingReporter() {
        return new SpanCaptor();
    }

    @Bean
    public HelloApi helloClient() {
        return RetrofitServiceClientBuilder
            .createOver(HelloApi.class, getBaseUrl())
            .metrics(meterRegistry)
            .httpTracing(httpTracing)
            .buildClient();
    }

    private String getBaseUrl() {
        return Optional.ofNullable(mockServerUrl).filter(s -> !s.isBlank()).orElseGet(SpringBootWebFluxIntegrationTest::getMockServerUrl);
    }
}
