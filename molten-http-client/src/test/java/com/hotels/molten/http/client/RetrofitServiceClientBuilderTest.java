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

import static com.hotels.molten.http.client.ReactiveTestSupport.anErrorWith;
import static com.hotels.molten.http.client.Response.response;
import static com.hotels.molten.trace.test.TracingTestSupport.capturedSpans;
import static com.hotels.molten.trace.test.TracingTestSupport.resetCapturedSpans;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.isA;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import brave.ScopedSpan;
import brave.Tracing;
import brave.http.HttpTracing;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.http.HttpVersion;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;
import zipkin2.Span;

import com.hotels.molten.core.mdc.MoltenMDC;
import com.hotels.molten.core.metrics.MoltenMetrics;
import com.hotels.molten.healthcheck.CompositeHealthIndicator;
import com.hotels.molten.healthcheck.Health;
import com.hotels.molten.healthcheck.HealthIndicator;
import com.hotels.molten.healthcheck.Status;
import com.hotels.molten.http.client.tracking.DefaultRequestTrackingFactory;
import com.hotels.molten.test.AssertSubscriber;
import com.hotels.molten.trace.test.AbstractTracingTest;

/**
 * Integration test for {@link RetrofitServiceClientBuilder}.
 */
@Slf4j
public class RetrofitServiceClientBuilderTest extends AbstractTracingTest {
    private static final AtomicInteger GRP_ID = new AtomicInteger();
    private static final StubJsonServiceServer SERVER = new StubJsonServiceServer();
    private static final String BASE_URL = "http://localhost:" + SERVER.getPort() + "/";

    @BeforeClass
    public void initServer() {
        LOG.info("Starting stub server.");
        MoltenMDC.initialize();
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        SERVER.start(true, false);
        ServiceEndpoint warmupClient = defaultClientBuilder("NETTY")
            .expectedLoad(ExpectedLoad.builder().peakResponseTime(Duration.ofSeconds(20)).peakRequestRatePerSecond(1).build())
            .connectionSettings(ConnectionSettings.builder().timeout(Duration.ofSeconds(20)).keepAliveIdle(Duration.ofMinutes(15)).build())
            .maxRetries(2)
            .buildClient();
        StepVerifier.create(warmupClient.getData("warmup").retry(1)).expectNext(response("warmup", 1)).verifyComplete();
        System.setProperty("MOLTEN_HTTP_CLIENT_REPORT_TRACE_com_hotels_molten_http_client_ServiceEndpoint", "false");
        System.setProperty("MOLTEN_HTTP_CLIENT_LOG_DETAILED_TRACE_com_hotels_molten_http_client_ServiceEndpoint", "false");
        System.setProperty("MOLTEN_HTTP_CLIENT_LOG_TRACE_com_hotels_molten_http_client_ServiceEndpoint", "false");
        System.setProperty("MOLTEN_HTTP_CLIENT_REACTOR_TRACE_LOG_ENABLED", "false");
    }

    @AfterClass
    public void tearDownContext() {
        SERVER.shutDown();
        MoltenMetrics.setDimensionalMetricsEnabled(false);
        MoltenMDC.uninitialize();
    }

    @AfterMethod
    public void clearContext() {
        MDC.clear();
        resetCapturedSpans();
        System.clearProperty("MOLTEN_HTTP_CLIENT_REPORT_TRACE_com_hotels_molten_http_client_ServiceEndpoint");
        System.clearProperty("MOLTEN_HTTP_CLIENT_LOG_TRACE_com_hotels_molten_http_client_ServiceEndpoint");
        System.clearProperty("MOLTEN_HTTP_CLIENT_LOG_DETAILED_TRACE_com_hotels_molten_http_client_ServiceEndpoint");
        System.clearProperty("MOLTEN_HTTP_CLIENT_REACTOR_TRACE_LOG_ENABLED");
    }

    @DataProvider(name = "common")
    public Object[][] getCommonTests() {
        return new Object[][] {
            new Object[] {"NETTY"},
            new Object[] {"OKHTTP"}
        };
    }

    @DataProvider(name = "netty")
    public Object[][] getNettyOnlyTests() {
        return new Object[][] {
            new Object[] {"NETTY"}
        };
    }

    @DataProvider(name = "okhttp")
    public Object[][] getOkHttpOnlyTests() {
        return new Object[][] {
            new Object[] {"OKHTTP"}
        };
    }

    @Test(dataProvider = "common")
    public void should_get_response(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .protocol(List.of(Protocols.HTTP_2C))
            .buildClient();
        StepVerifier.create(client.getData("test"))
            .thenAwait()
            .expectNext(response("test", 1))
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_get_responseWhen_called_With_HTTP_1_1(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .protocol(List.of(Protocols.HTTP_1_1))
            .buildClient();
        StepVerifier.create(client.checkProtocol(HttpVersion.HTTP_1_1))
            .thenAwait()
            .expectNextCount(1L)
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_get_responseWhen_called_With_HTTP_2C(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .protocol(List.of(Protocols.HTTP_2C))
            .buildClient();
        StepVerifier.create(client.checkProtocol(HttpVersion.HTTP_2))
            .thenAwait()
            .expectNextCount(1L)
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_get_query_response(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType).buildClient();
        StepVerifier.create(client.getQuery("test"))
            .thenAwait()
            .expectNext(response("test", 1))
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_get_xml_response(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .objectMapper(new XmlMapper(new JacksonXmlModule()))
            .buildClient();
        StepVerifier.create(client.getDataAsXml("test"))
            .thenAwait()
            .expectNext(response("test", 1))
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_post(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType).buildClient();
        StepVerifier.create(client.postData(new Request("text", 13)))
            .thenAwait()
            .expectNext(response("text", 13))
            .verifyComplete();
    }

    @Test(dataProvider = "netty") //TODO: fix this with okhttp backend
    public void should_send_proper_content_type_with_post(String clientType) {
        var client = defaultClientBuilder(clientType).buildClient();
        StepVerifier.create(client.contentType(new Request("json", 13)))
            .thenAwait()
            .expectNext(response("json", 13))
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    @Ignore
    //FIXME: retrofit2.converter.jackson.JacksonConverterFactory is wrong as it always assumes application/json as content type
    public void should_send_proper_content_type_with_post_for_xml(String clientType) {
        var client = defaultClientBuilder(clientType).buildClient();

        client = defaultClientBuilder(clientType)
            .objectMapper(new XmlMapper(new JacksonXmlModule()))
            .buildClient();
        StepVerifier.create(client.contentType(new Request("xml", 42)))
            .thenAwait()
            .expectNext(response("xml", 42))
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_delete(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType).buildClient();
        StepVerifier.create(client.delete("test"))
            .thenAwait()
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_send_multiple_headers(String clientType) {
        var client = defaultClientBuilder(clientType).buildClient();
        StepVerifier.create(client.getHeader("CustomHeader", "a"))
            .thenAwait()
            .expectNext("a")
            .verifyComplete();
        StepVerifier.create(client.getHeaders("CustomHeader", List.of("a", "b")))
            .thenAwait()
            .expectNext("a,b")
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_return_empty(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType).buildClient();
        StepVerifier.create(client.find("empty"))
            .thenAwait()
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_return_maybe_value(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType).buildClient();
        StepVerifier.create(client.find("test"))
            .thenAwait()
            .expectNext(response("test", 1))
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_emit_permanent_exception_for_permanent_error(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType).buildClient();
        StepVerifier.create(client.getStatus(400))
            .thenAwait()
            .verifyErrorSatisfies(ex -> assertThat(ex).isInstanceOf(PermanentServiceInvocationException.class)
                .hasCauseInstanceOf(HttpException.class)
                .satisfies(e -> assertThat(e.getCause()).hasCauseInstanceOf(retrofit2.HttpException.class)
                    .hasMessage("http_status=400 error_message=Bad Request error_body=\"error\"")));
    }

    @Test(dataProvider = "common")
    public void should_emit_temporary_exception_for_temporary_failure(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType).buildClient();
        StepVerifier.create(client.getStatus(503))
            .thenAwait()
            .verifyError(TemporaryServiceInvocationException.class);
    }

    @Test(dataProvider = "common")
    public void should_emit_temporary_exception_if_request_times_out(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .expectedLoad(ExpectedLoad.builder()
                .peakResponseTime(Duration.ofMillis(200))
                .averageResponseTime(Duration.ofMillis(20))
                .peakRequestRatePerSecond(1)
                .build())
            .buildClient();

        StepVerifier.create(client.getDelayed(300))
            .thenAwait()
            .verifyError(TemporaryServiceInvocationException.class);
    }

    @Test(dataProvider = "common")
    public void should_limit_concurrency(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .expectedLoad(ExpectedLoad.builder()
                .peakResponseTime(Duration.ofMillis(950))
                .averageResponseTime(Duration.ofMillis(20))
                .peakRequestRatePerSecond(1) // with these settings concurrency is 1
                .build())
            .maxRetries(0)
            .buildClient();

        AssertSubscriber<String> subscriber = new AssertSubscriber<>();
        AssertSubscriber<String> subscriber2 = new AssertSubscriber<>();
        LOG.info("Invoking long-running request");
        client.getDelayed(500).subscribe(subscriber);
        LOG.info("Invoking next request");
        client.getDelayed(100).subscribe(subscriber2);
        // CB will kick in so it finishes but with error
        subscriber2.assertErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(BulkheadFullException.class)));
    }

    @Test(dataProvider = "common")
    public void should_retry_temporary_failure(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .expectedLoad(ExpectedLoad.builder()
                .peakResponseTime(Duration.ofMillis(300))
                .averageResponseTime(Duration.ofMillis(20))
                .peakRequestRatePerSecond(1)
                .build())
            .maxRetries(2)
            .buildClient();

        StepVerifier.create(client.getMaybe(3, 503))
            .thenAwait()
            .expectNext(StubJsonServiceServer.SUCCESS)
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_support_resubscribes(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .expectedLoad(ExpectedLoad.builder()
                .peakResponseTime(Duration.ofMillis(300))
                .averageResponseTime(Duration.ofMillis(20))
                .peakRequestRatePerSecond(1)
                .build())
            .logHttpEvents()
            .maxRetries(0)
            .buildClient();

        StepVerifier.create(client.getMaybe(3, 503).retry(2))
            .thenAwait()
            .expectNext(StubJsonServiceServer.SUCCESS)
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_timeout_request(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .expectedLoad(ExpectedLoad.builder()
                .peakResponseTime(Duration.ofMillis(300))
                .averageResponseTime(Duration.ofMillis(20))
                .peakRequestRatePerSecond(1)
                .build())
            .logHttpEvents()
            .buildClient();

        StepVerifier.create(client.getDelayed(400))
            .thenAwait()
            .verifyErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(TimeoutException.class)));
    }

    @Test(dataProvider = "netty")
    @Ignore
    public void skeleton_to_test_things_with_real_metrics(String clientType) throws InterruptedException {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .maxRetries(0)
            .expectedLoad(ExpectedLoad.builder()
                .peakResponseTime(Duration.ofMillis(2000))
                .averageResponseTime(Duration.ofMillis(20))
                .peakRequestRatePerSecond(200)
                .averageRequestRatePerSecond(1)
                .build())
            .recovery(RecoveryConfiguration.builder()
                .failureThreshold(0.5F)
                .responsiveness(Duration.ofSeconds(1))
                .build())
            .connectionSettings(ConnectionSettings.builder()
                .retryOnConnectionFailure(false)
                .keepAliveIdle(Duration.ofSeconds(30))
                .timeout(Duration.ofMillis(1000))
                .build())
            .metrics(MeterRegistrySupport.localGraphiteRegistry())
            .logHttpEvents()
            .reportHttpEvents()
            .buildClient();

        LOG.info("Starting requests");
        Random rng = new Random(1);
        Flux.interval(Duration.ofMillis(25))
            .flatMap(i -> client.getDelayed(rng.nextInt(200))
                .subscribeOn(Schedulers.parallel())
                .doOnError(e -> e instanceof TemporaryServiceInvocationException && e.getCause().getMessage().startsWith("Socket is closed"),
                    e -> LOG.error("Socket closed error", e))
                .doOnError(e -> LOG.info("client error {}", e.getMessage()))
                .onErrorReturn("error"))
            .subscribe(i -> LOG.info("result {}", i), e -> LOG.error("error {}", e));
        Thread.sleep(600000);
    }

    @Test(dataProvider = "common")
    public void should_open_circuit_after_too_many_failed_request(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .expectedLoad(ExpectedLoad.builder()
                .peakResponseTime(Duration.ofMillis(800))
                .averageResponseTime(Duration.ofMillis(20))
                .peakRequestRatePerSecond(30)
                .averageRequestRatePerSecond(25)
                .build())
            .recovery(RecoveryConfiguration.builder()
                .failureThreshold(0.2F)
                .responsiveness(Duration.ofSeconds(1))
                .countBasedRecovery()
                .build())
            .buildClient();
        //let's fill the bitsetring: 1 sec responsiveness * 25 avg rps = 25
        //let's add 24 errors, CB is still closed as it is not full yet (24 calls registered)
        StepVerifier.create(Flux.range(0, 24).flatMap(i -> client.getStatus(503).onErrorReturn("doh"))).thenAwait().expectNextCount(24).verifyComplete();
        //last call is still http exception
        StepVerifier.create(client.getStatus(503)).thenAwait().verifyErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(HttpException.class)));
        //CB opens, next call is shortcircuited
        StepVerifier.create(client.getStatus(503))
            .thenAwait().verifyErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(CallNotPermittedException.class)));
    }

    @Test(dataProvider = "common")
    public void should_close_circuit_after_enough_successful_request(String clientType) throws InterruptedException {
        CompositeHealthIndicator testIndicator = HealthIndicator.composite("test");
        var testSubscriber = AssertSubscriber.<Health>create();
        testIndicator.health().subscribe(testSubscriber);
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .expectedLoad(ExpectedLoad.builder()
                .peakResponseTime(Duration.ofMillis(800))
                .averageResponseTime(Duration.ofMillis(20))
                .peakRequestRatePerSecond(30)
                .averageRequestRatePerSecond(25)
                .build())
            .recovery(RecoveryConfiguration.builder()
                .failureThreshold(20F)
                .responsiveness(Duration.ofSeconds(1))
                .countBasedRecovery()
                .build())
            .healthIndicatorWatcher(testIndicator)
            .buildClient();
        testSubscriber.assertValues(healths -> assertThat(healths).extracting(Health::status).containsSequence(Status.UP));
        // the above settings will yield a ring size of 25 for closed state and ring size of 10 for halfopen state. let's fill it
        StepVerifier.create(Flux.range(0, 25).concatMap(i -> client.getStatus(503).onErrorReturn("doh"))).thenAwait().expectNextCount(25).verifyComplete();
        // now the CB is open
        testSubscriber.assertValues(healths -> assertThat(healths).extracting(Health::status).containsSequence(Status.UP, Status.DOWN));
        StepVerifier.create(client.getStatus(200))
            .thenAwait().verifyErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(CallNotPermittedException.class)));
        // let's wait 1 sec for the back-off window
        Thread.sleep(Duration.ofMillis(1500).toMillis());
        // now the CB should permit calls in half-open state
        testSubscriber.assertValues(healths -> assertThat(healths).extracting(Health::status).containsSequence(Status.UP, Status.DOWN, Status.STRUGGLING));
        StepVerifier.create(Flux.range(0, 10).flatMap(i -> Flux.defer(() -> client.getStatus(200)))).thenAwait().expectNextCount(10).verifyComplete();
        // the CB is closed again, we might get errors from deeper
        testSubscriber.assertValues(healths -> assertThat(healths).extracting(Health::status).containsSequence(Status.UP, Status.DOWN, Status.STRUGGLING, Status.UP));
        StepVerifier.create(client.getStatus(503)).thenAwait()
            .verifyErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(com.hotels.molten.http.client.HttpException.class)));
        testSubscriber.assertValues(healths -> assertThat(healths)
            .extracting(Health::status)
            .containsSequence(Status.UP, Status.DOWN, Status.STRUGGLING, Status.UP));
    }

    @Test(dataProvider = "common")
    public void should_close_circuit_even_if_encountering_error_during_half_open_state(String clientType) throws InterruptedException {
        CompositeHealthIndicator testIndicator = HealthIndicator.composite("test");
        var testSubscriber = AssertSubscriber.<Health>create();
        testIndicator.health().take(4).subscribe(testSubscriber);
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .maxRetries(0)
            .expectedLoad(ExpectedLoad.builder()
                .peakResponseTime(Duration.ofMillis(800))
                .averageResponseTime(Duration.ofMillis(20))
                .peakRequestRatePerSecond(30)
                .averageRequestRatePerSecond(25)
                .build())
            .recovery(RecoveryConfiguration.builder()
                .failureThreshold(30F)
                .responsiveness(Duration.ofSeconds(1))
                .countBasedRecovery()
                .build())
            .healthIndicatorWatcher(testIndicator)
            .buildClient();
        // the above settings will yield a ring size of 25 for closed state and ring size of 10 for half-open state. let's fill it
        StepVerifier.create(Flux.range(0, 25)
            .concatMap(i -> client.getStatus(503)
                .retryWhen(Retry.indefinitely()
                    .filter(throwable -> throwable instanceof TemporaryServiceInvocationException && throwable.getCause() instanceof BulkheadFullException))
                .onErrorReturn("doh")))
            .thenAwait().expectNextCount(25).verifyComplete();
        // now the CB is open
        StepVerifier.create(client.getStatus(200))
            .thenAwait().verifyErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(CallNotPermittedException.class)));
        // let's wait 1 sec for the back-off window
        Thread.sleep(Duration.ofMillis(1500).toMillis());
        // now the CB should permits calls in half-open state
        StepVerifier.create(Flux.range(0, 8).flatMap(i -> client.getStatus(200))).thenAwait().expectNextCount(8).verifyComplete();
        // encountering ignored exception by CB during half-open state
        StepVerifier.create(Flux.range(0, 2).flatMap(i -> Flux.defer(() -> client.getStatus(400).onErrorReturn("doh"))))
            .thenAwait().expectNextCount(2).verifyComplete();
        // let's wait a bit to let CB recover
        Thread.sleep(Duration.ofMillis(1500).toMillis());
        // the CB should be in half-open state again and allow requests
        StepVerifier.create(client.getStatus(200)).thenAwait().expectNextCount(1).verifyComplete();
        testSubscriber.assertValues(healths -> assertThat(healths)
            .extracting(Health::status)
            .containsSequence(Status.UP, Status.DOWN, Status.STRUGGLING, Status.UP));
    }

    @Test(dataProvider = "common")
    public void should_track_circuit_per_method(String clientType) throws InterruptedException {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            // will yield concurrency of 9
            .expectedLoad(ExpectedLoad.builder()
                .peakResponseTime(Duration.ofMillis(800))
                .averageResponseTime(Duration.ofMillis(20))
                .peakRequestRatePerSecond(10)
                .build())
            .buildClient();
        StepVerifier.create(client.getStatus(400)).thenAwait().verifyErrorSatisfies(anErrorWith(PermanentServiceInvocationException.class, isA(HttpException.class)));
        IntStream.range(0, 5)
            .forEach(
                i -> StepVerifier.create(client.getStatus(503)).thenAwait()
                    .verifyErrorSatisfies(anErrorWith(TemporaryServiceInvocationException.class, isA(HttpException.class))));
        Thread.sleep(300); // so CB statistics are updated for sure
        // this method is tracked separately
        StepVerifier.create(client.getData("test")).thenAwait().expectNext(response("test", 1)).verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_send_request_tracking_headers(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType).requestTracking(new DefaultRequestTrackingFactory("my-client").createRequestTracking()).buildClient();

        MDC.put("requestId", "request id");
        MDC.put("sessionId", "session id");

        StepVerifier.create(client.getHeader("User-Agent", "anything")).thenAwait().expectNext("my-client").verifyComplete();
        StepVerifier.create(client.getHeader("X-Message-Group-ID", "anything")).thenAwait().expectNext("request id").verifyComplete();
        StepVerifier.create(client.getHeader("X-Session-ID", "anything")).thenAwait().expectNext("session id").verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_send_request_tracking_headers_when_switching_schedulers(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType).requestTracking(new DefaultRequestTrackingFactory("my-client").createRequestTracking()).buildClient();

        MDC.put("requestId", "request id");

        StepVerifier.create(client.getHeader("X-Message-Group-ID", "anything")
            .subscribeOn(Schedulers.parallel()))
            .thenAwait()
            .expectNext("request id")
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_maintain_mdccontext_after_call_returned(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType).requestTracking(new DefaultRequestTrackingFactory("my-client").createRequestTracking()).buildClient();

        MDC.put("requestId", "request id");

        StepVerifier.create(client.getHeader("X-Message-Group-ID", "anything")
            .subscribeOn(Schedulers.parallel())
            .map(response -> MDC.get("requestId")))
            .thenAwait()
            .expectNext("request id")
            .verifyComplete();
    }

    @Test(dataProvider = "common")
    public void should_be_possible_to_get_response_headers_with_body(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType).requestTracking(new DefaultRequestTrackingFactory("my-client").createRequestTracking()).buildClient();

        StepVerifier.create(client.pingHeader("whatever"))
            .thenAwait()
            .expectNextMatches(response -> Objects.equals(new Response("value", 42), response.body())
                && Objects.equals(response.headers().get("pong"), "whatever"))
            .verifyComplete();
    }

    @Test(dataProvider = "netty")
    public void should_be_possible_to_get_response_headers_with_body_and_with_metrics(String clientType) {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .requestTracking(new DefaultRequestTrackingFactory("my-client").createRequestTracking())
            .metrics(meterRegistry)
            .logHttpEvents()
            .reportHttpEvents()
            .buildClient();
        String expectedMetricPrefix = "client.grp" + GRP_ID.get();

        StepVerifier.create(client.pingHeader("whatever"))
            .thenAwait()
            .expectNextMatches(response -> Objects.equals(new Response("value", 42), response.body())
                && Objects.equals(response.headers().get("pong"), "whatever"))
            .verifyComplete();

        assertThat(meterRegistry.getMeters().stream().filter(m -> m instanceof Timer).collect(Collectors.toList()))
            .extracting(Meter::getId)
            .extracting(Meter.Id::getName)
            .contains(
                expectedMetricPrefix + ".http-trace.duration.resolve-address",
                expectedMetricPrefix + ".http-trace.duration.connect",
                expectedMetricPrefix + ".http-trace.duration.data-sent",
                expectedMetricPrefix + ".http-trace.duration.data-received",
                expectedMetricPrefix + ".http-trace.duration.response"
            );
    }

    @Test(dataProvider = "netty")
    public void should_be_possible_to_get_response_headers_with_body_and_with_metrics_forced_from_system_props(String clientType) {
        System.setProperty("MOLTEN_HTTP_CLIENT_REPORT_TRACE_com_hotels_molten_http_client_ServiceEndpoint", "true");
        System.setProperty("MOLTEN_HTTP_CLIENT_LOG_TRACE_com_hotels_molten_http_client_ServiceEndpoint", "true");
        System.setProperty("MOLTEN_HTTP_CLIENT_LOG_DETAILED_TRACE_com_hotels_molten_http_client_ServiceEndpoint", "true");
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .requestTracking(new DefaultRequestTrackingFactory("my-client").createRequestTracking())
            .metrics(meterRegistry)
            .buildClient();
        String expectedMetricPrefix = "client.grp" + GRP_ID.get();

        StepVerifier.create(client.pingHeader("whatever"))
            .thenAwait()
            .expectNextMatches(response -> Objects.equals(new Response("value", 42), response.body())
                && Objects.equals(response.headers().get("pong"), "whatever"))
            .verifyComplete();

        assertThat(meterRegistry.getMeters().stream().filter(m -> m instanceof Timer).collect(Collectors.toList()))
            .extracting(Meter::getId)
            .extracting(Meter.Id::getName)
            .contains(
                expectedMetricPrefix + ".http-trace.duration.response"
            );
    }

    @Test(dataProvider = "common")
    public void should_be_forbidden_to_return_with_result(String clientType) {
        IllegalServiceEndpoint illegalServiceEndpoint = RetrofitServiceClientBuilder.createOver(IllegalServiceEndpoint.class, BASE_URL)
            .groupId("grp" + GRP_ID.incrementAndGet())
            .buildClient();

        StepVerifier.create(illegalServiceEndpoint.resetCounter())
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(PermanentServiceInvocationException.class).hasCauseInstanceOf(IllegalArgumentException.class);
                assertThat(error.getCause()).hasMessageStartingWith("Unable to create call adapter for").hasCauseInstanceOf(IllegalStateException.class);
                assertThat(error.getCause().getCause()).hasMessage("Result as the return type of an API method is forbidden in molten. Please use retrofit2.Response instead.");
            })
            .verify();
    }

    @Test(dataProvider = "common")
    public void should_propagate_trace_context(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .httpTracing(HttpTracing.current().clientOf("test-server"))
            .buildClient();
        assertTraceContextIsPropagated(client.getHeader("X-B3-TraceId", "anything"));
    }

    @Test(dataProvider = "common")
    public void should_send_zipkin_headers(String clientType) {
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .httpTracing(HttpTracing.current().clientOf("test-server"))
            .buildClient();
        ScopedSpan outer = Tracing.currentTracer().startScopedSpan("outer");
        var expectedTraceId = Tracing.currentTracer().currentSpan().context().traceIdString();
        var expectedSpanId = Tracing.currentTracer().currentSpan().context().spanIdString();
        try {
            // test that trace id is propagated with the call in header
            StepVerifier.create(client.getHeader("X-B3-TraceId", "anything")).thenAwait().expectNext(expectedTraceId).verifyComplete();
        } finally {
            outer.finish();
        }
        List<Span> capturedSpans = capturedSpans();
        assertThat(capturedSpans)
            .extracting(Span::name)
            .contains("get /header/:name", "get", "outer");

        var rootSpan = assertThat(capturedSpans)
            .filteredOn("name", "outer")
            .first();
        rootSpan.extracting(Span::traceId).isEqualTo(expectedTraceId);
        rootSpan.extracting(Span::id).isEqualTo(expectedSpanId);

        var clientSpan = assertThat(capturedSpans)
            .filteredOn("name", "get")
            .first();
        clientSpan.extracting(Span::traceId).isEqualTo(expectedTraceId);
        clientSpan.extracting(Span::kind).isEqualTo(Span.Kind.CLIENT);
        clientSpan.extracting(Span::parentId).isEqualTo(expectedSpanId);

        var serverSpan = assertThat(capturedSpans)
            .filteredOn("name", "get /header/:name")
            .first();
        serverSpan.extracting(Span::traceId).isEqualTo(expectedTraceId);
        serverSpan.extracting(Span::kind).isEqualTo(Span.Kind.SERVER);
        //TODO: check if this is expected or it should rather be child of get
        serverSpan.extracting(Span::parentId).isEqualTo(expectedSpanId);
    }

    @Test(dataProvider = "common")
    public void should_report_initial_ok_health_check(String clientType) {
        CompositeHealthIndicator testHealthIndicator = HealthIndicator.composite("test");
        ServiceEndpoint client = defaultClientBuilder(clientType)
            .healthIndicatorWatcher(testHealthIndicator)
            .buildClient();
        StepVerifier.create(client.getStatus(200)).thenAwait().expectNext("success").verifyComplete();
        StepVerifier.create(testHealthIndicator.health().take(1))
            .expectNextMatches(Health::healthy)
            .verifyComplete();
        assertThat(testHealthIndicator.subIndicators()).singleElement().satisfies(healthIndicator -> {
            assertThat(healthIndicator).isInstanceOf(CompositeHealthIndicator.class);
            assertThat(healthIndicator.name()).isEqualTo("http_client.grp" + GRP_ID.get());
            assertThat(((CompositeHealthIndicator) healthIndicator).subIndicators()).singleElement().satisfies(methodIndicator -> {
                assertThat(methodIndicator.name()).endsWith("_getStatus");
                StepVerifier.create(methodIndicator.health().take(1)).expectNextMatches(Health::healthy).verifyComplete();
            });
        });
    }

    private RetrofitServiceClientBuilder<ServiceEndpoint> defaultClientBuilder(String clientType) {
        System.setProperty("MOLTEN_HTTP_CLIENT_DEFAULT_TYPE", clientType);
        return RetrofitServiceClientBuilder.createOver(ServiceEndpoint.class, BASE_URL)
            .groupId("grp" + GRP_ID.incrementAndGet());
    }
}
