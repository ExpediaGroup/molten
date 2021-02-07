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
package com.hotels.molten.spring.boot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import zipkin2.Span;

import com.hotels.molten.spring.boot.integration.test.LogCaptor;
import com.hotels.molten.spring.boot.integration.test.SpanCaptor;
import com.hotels.molten.spring.boot.integration.test.TestApplication;

/**
 * These tests ensure that Molten HTTP client can be used with spring-boot webflux without issues.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApplication.class)
@Testcontainers
public class SpringBootWebFluxIntegrationTest {
    @Container
    private static final MockServerContainer MOCK_SERVER = new MockServerContainer(DockerImageName.parse("jamesdbloom/mockserver:mockserver-5.11.2"));
    @Autowired
    private WebTestClient webClient;

    @BeforeAll
    static void initMockServer() {
        System.setProperty("MOLTEN_HTTP_CLIENT_DEFAULT_TYPE", "OKHTTP");
        new MockServerClient(MOCK_SERVER.getHost(), MOCK_SERVER.getServerPort())
            .when(request()
                .withPath("/hello")
                .withQueryStringParameter("name", "Bob"))
            .respond(response()
                .withBody("\"Hello Bob!\""));
    }

    public static String getMockServerUrl() {
        return MOCK_SERVER.getEndpoint();
    }

    @Test
    public void should_integrate_with_webflux() {
        webClient.get().uri("/say-hello").exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("Hello Bob!");
    }

    @Test
    public void should_integrate_with_prometheus() {
        webClient.get().uri("/say-hello").exchange()
            .expectStatus().isOk();
        webClient.get().uri("/actuator/prometheus").exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .consumeWith(b -> assertThat(b.getResponseBody())
                .contains("http_server_requests_seconds{exception=\"None\",method=\"GET\",outcome=\"SUCCESS\",status=\"200\",uri=\"/say-hello\"")
                .contains("http_client_requests_seconds{client=\"com_hotels_molten_spring_boot_integration_test_HelloApi\",endpoint=\"greet\","
                    + "status=\"success\",type=\"total\",quantile=\"0.5\",}"));
    }

    @RepeatedTest(3)
    public void should_integrate_with_mdc() {
        // This call is just to ensure that former calls don't affect later ones.
        // The order of MdcWebFilter must be lower (higher precedence) than the other filters doing any logging (e.g. TraceWebFilter in this case).
        webClient.get().uri("/say-hello").exchange()
            .expectStatus().isOk().expectBody();
        LogCaptor.clearCapturedLogs();
        // Post has more complicated threading due to reactive body handling.
        var requestId = webClient.post().uri("/request-id").bodyValue("thing").exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();
        assertThat(LogCaptor.getCapturedLogs()).allSatisfy(log -> assertThat(log.getMdc()).hasEntrySatisfying("request-id", id -> assertThat(id).isEqualTo(requestId)));
    }

    @RepeatedTest(3)
    public void should_work_with_sleuth_tracing() {
        // This call is to rule out former calls potential effect on the tested ones.
        webClient.get().uri("/say-hello").exchange()
            .expectStatus().isOk().expectBody();
        SpanCaptor.resetCapturedSpans();
        assertThat(SpanCaptor.capturedSpans()).isEmpty();
        webClient.get().uri("/say-hello").exchange()
            .expectStatus().isOk().expectBody();
        assertThat(SpanCaptor.capturedSpans())
            .anySatisfy(span -> {
                assertThat(span).extracting(Span::parentId).isNull();
                assertThat(span).extracting(Span::name).isEqualTo("get /say-hello");
            });
        var rootSpan = SpanCaptor.capturedSpans().stream().filter(span -> "get /say-hello".equals(span.name())).findFirst().orElseThrow();
        assertThat(SpanCaptor.capturedSpans())
            .anySatisfy(span -> {
                assertThat(span).extracting(Span::parentId).isEqualTo(rootSpan.id());
                assertThat(span).extracting(Span::name).isEqualTo("get");
                assertThat(span).extracting(Span::tags).satisfies(tags -> assertThat(tags).containsEntry("http.path", "/hello").containsEntry("http.method", "GET"));
            });
    }
}
