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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.hotels.molten.http.client.HttpServiceInvocationExceptionHandlingReactiveProxyFactory.DEFAULT_FAILED_RESPONSE_PREDICATE;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import brave.http.HttpTracing;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jakewharton.retrofit2.adapter.reactor.ReactorCallAdapterFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.Retrofit.Builder;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.converter.protobuf.ProtoConverterFactory;

import com.hotels.molten.healthcheck.CompositeHealthIndicator;
import com.hotels.molten.healthcheck.HealthIndicator;
import com.hotels.molten.healthcheck.HealthIndicatorWatcher;
import com.hotels.molten.http.client.tracking.RequestTracking;

/**
 * Generic factory to create a reactive non-blocking HTTP client over a JSON/XML service using its interface using Retrofit, OkHttp and resilience4j.
 *
 * <p>
 * The call stack from outside in as follows:
 * <ol>
 * <li>client call tracing</li>
 * <li>total times metrics</li>
 * <li>call log</li>
 * <li>bulkhead</li>
 * <li>circuit breaker</li>
 * <li>total timeout</li>
 * <li>retry</li>
 * <li>raw timeout</li>
 * <li>raw times metrics</li>
 * <li>HTTP hiding layer</li>
 * <li>Retrofit</li>
 * <li>OK Http</li>
 * </ol>
 *
 * <h3>Connection</h3>
 * Uses a connection pool to be able to reuse connections. Connection pool settings can be configured with {@link #connectionSettings(ConnectionSettings)}.
 * See <i>Isolation</i> section for more details how connections are reused within an isolation group.
 *
 * <h3>Timeout</h3>
 * Timeouts are based on peak and average response times, and are applied on top of raw calls (peak response time as timeout)
 * and above retried calls {@code totalTimeout = peakResponseTimeInMs * max(retries, 1) + (retries > 0 ? averageResponseTimeInMs : 0)} as total timeout.
 * Timeouts can be set with {@link #expectedLoad(ExpectedLoad)}.
 *
 * <h3>Concurrency</h3>
 * Connection pool and bulkheading is configured according to calculated concurrency based on expected load settings ({@link #expectedLoad(ExpectedLoad)}).
 * Formula is {@code concurrency = ceil(totalTimeout * peakRequestRatePerSecond / 1000)}.
 * For example for {@code peakResponseTimeInMs=400, averageResponseTimeInMs=100, retries=2, peakRequestRatePerSecond=2} the concurrency will be 2.
 * Defaults are peak load 1sec@10RPS, average load 100ms@5RPS (RPS = request per sec). The least request rate this client can handle is 1 request per second.
 * Maximum concurrency is enforced by a bulkhead on top of each method on the API. See <i>Isolation</i> section for more details.
 *
 * <h3>Circuit breaking</h3>
 * Circuit breaker is configured based on expected recovery parameters (failure rate and maximum time to respond for failure aka responsiveness) and request rates using
 * {@link #recovery(RecoveryConfiguration)}.
 * <p>
 * The average time to react to failures and opening the circuit is the responsiveness defined in seconds at the average request rate but not less than 20 calls.
 * The average time to react to successes and closing the circuit is 1 second at peak request rates but not more than 10 calls.
 * Defaults are 25% failure rate and 30 seconds responsiveness.
 * <p>
 * Fine-tuning the circuit breaker is also possible with {@link RecoveryConfiguration.Builder#numberOfCallsToConsiderForHealthyCircuit(int)} and
 * {@link RecoveryConfiguration.Builder#numberOfCallsToConsiderForRecoveringCircuit(int)}.
 *
 * <h3>Isolation</h3>
 * Please note that unless set with {@link #groupId(String)} the client will use an *isolation group* ID generated from API class name.
 * This group ID must be unique and cannot be reused. It ensures that all the clients created with this builder will be isolated from each other.
 * This also means that all the methods on the API will use the same isolation group and a single shared connection pool and dispatcher.
 * Should you need more fine-grained isolation, you should split the API interface and generate separate clients for them.
 * <p>
 * Circuit breaker and bulkhead are defined per isolation group + method name. This means that methods with the same name will share circuit breaker and bulkhead as well.
 * <strong>It is advisable to have unique method names on the API.</strong>
 *
 * <h3>Metrics</h3>
 * Common metrics are prefixed with isolation group: {@code client.[isolationGroupName].*}.
 * {@code isolationGroupName} is automatically set to fully qualified API class name (with {@code .} replaced with {@code _}) unless overridden.
 * <ul>
 *     <li>connection pool (for okhttp)</li>
 *     <li>dispatcher (for okhttp)</li>
 *     <li>http trace when enabled</li>
 * </ul>
 * Method specific metrics are prefixed with isolation group and method name: {@code client.[isolationGroupName].[methodName].*}
 * <ul>
 *     <li>raw response times (below circuit breaker, per HTTP request)</li>
 *     <li>timeout metrics</li>
 *     <li>number of retries</li>
 *     <li>circuit breaker metrics</li>
 *     <li>bulkhead metrics</li>
 *     <li>total response time (above circuit breaker, including retries)</li>
 * </ul>
 * The client final configuration is also reported at {@code client.[isolationGroupName].config.*}
 *
 * <h3>Retry</h3>
 * By default retries requests with temporary failure once. There's no delay between retries. For more sophisticated retry logic use Reactor's retry strategies.
 *
 * <h3>Serialization</h3>
 * Serialization works with Jackson and by default {@link Jdk8Module} and {@link JavaTimeModule} are registered. Default is JSON format.
 * Custom {@link ObjectMapper} can be set to support custom serialization for JSON or XML with {@code com.fasterxml.jackson.dataformat.xml.XmlMapper}.
 * Protobuf can also be enabled calling the {@link #useProtobuf()} method.
 *
 * <h3>Execution threads</h3>
 * Execution switches observation to computational scheduler to avoid being stuck on a HTTP request thread.
 *
 * <h3>Distributed tracing</h3>
 * Distributed tracing is also supported when {@link #httpTracing(HttpTracing)} is provided.
 * The whole client call will be traced along with each subsequent HTTP calls.
 * The HTTP calls will also propagate current tracing context with HTTP headers according to Zipkin's B3 specification.
 *
 * <h3>Certificates</h3>
 * HTTPS certificates are supported when {@link #customSSLContext(SSLContextConfiguration)} is provided.
 * {@link TlsSocketFactoryConfigFactory} can be used to create an {@link SslSocketFactoryConfig} from a keystore and a password.
 *
 * <h3>Tracing/debugging</h3>
 * There are several options to get low-level metrics and trace logs for http calls.
 * <p>
 * Metrics for http events can be enabled with {@link #reportHttpEvents()} or {@code MOLTEN_HTTP_CLIENT_REPORT_TRACE_fully_qualified_Api=true} system property.
 * <p>
 * Logging http events can be enabled with {@link #logHttpEvents()} or {@code MOLTEN_HTTP_CLIENT_LOG_TRACE_fully_qualified_Api=true} system property.
 * Verbose low-level http call tracing can be enabled using {@code MOLTEN_HTTP_CLIENT_LOG_DETAILED_TRACE_fully_qualified_Api=true} system property.
 * Logs are logged against API class FQN with INFO level.
 *
 * <h3>Client type</h3>
 * The http client is OkHttp (OKHTTP) by default. Reactor netty (NETTY) can also be used.
 * This can be controlled from system property with: {@code MOLTEN_HTTP_CLIENT_TYPE_fully_qualified_Api=[OKHTTP|NETTY]}.
 * For setting the client type for all clients in an application use {@code MOLTEN_HTTP_CLIENT_DEFAULT_TYPE=[OKHTTP|NETTY]}.
 *
 * @param <API> the actual type of API to build client for
 */
public final class RetrofitServiceClientBuilder<API> {
    static final String SYSTEM_PROP_PREFIX = "MOLTEN_HTTP_CLIENT_";
    private static final Set<String> USED_CLIENT_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Class<API> api;
    private final String baseUrl;
    private final RetrofitServiceClientConfiguration<API> configuration;
    private String customGroupId;
    private ObjectMapper objectMapper = defaultJsonObjectMapper();
    private boolean useProtobuf;
    private Predicate<Response<?>> failedResponsePredicate = DEFAULT_FAILED_RESPONSE_PREDICATE;
    private HealthIndicatorWatcher healthIndicatorWatcher = indicator -> { };

    private RetrofitServiceClientBuilder(Class<API> api, String baseUrl) {
        this.api = requireNonNull(api);
        this.baseUrl = requireNonNull(baseUrl);
        configuration = new RetrofitServiceClientConfiguration<>(api);
    }

    /**
     * Creates a client builder for a specific API and base URL.
     *
     * @param api     the API to create client for
     * @param baseUrl the base URL to use to fire requests against
     * @param <A>     the type of API
     * @return the client builder
     */
    public static <A> RetrofitServiceClientBuilder<A> createOver(Class<A> api, String baseUrl) {
        return new RetrofitServiceClientBuilder<>(api, baseUrl);
    }

    /**
     * Sets the service's unique ID by which it will be isolated from other client's service calls.
     * This ID is used to separate circuits, bulkheads, metrics and call logs as well.
     * If not set the canonical name of the API will be used.
     *
     * @param groupId the service group id
     * @return this builder
     */
    public RetrofitServiceClientBuilder<API> groupId(String groupId) {
        checkArgument(groupId != null, "Group ID must be non-negative.");
        this.customGroupId = groupId;
        return this;
    }

    /**
     * Sets the {@link HttpTracing} to be used for propagating Zipkin compatible distributed trace information.
     *
     * @param httpTracing the {@code httpTracing} to set
     * @return a reference to this Builder
     */
    public RetrofitServiceClientBuilder<API> httpTracing(HttpTracing httpTracing) {
        this.configuration.setHttpTracing(httpTracing);
        return this;
    }

    /**
     * Sets the expected load configuration. This affects final timeouts and capacities. See {@link RetrofitServiceClientBuilder} for details.
     *
     * @param expectedLoad the expected load configuration
     * @return this builder
     */
    public RetrofitServiceClientBuilder<API> expectedLoad(ExpectedLoad expectedLoad) {
        configuration.setExpectedLoad(expectedLoad);
        return this;
    }

    /**
     * Sets the recovery configuration.
     *
     * @param recoveryConfiguration the recovery configuration
     * @return this builder
     */
    public RetrofitServiceClientBuilder<API> recovery(RecoveryConfiguration recoveryConfiguration) {
        configuration.setRecoveryConfiguration(recoveryConfiguration);
        return this;
    }

    /**
     * Sets the maximum number of retries before giving up. There's no delay between retries.
     *
     * @param retries the number of retries
     * @return this builder
     */
    public RetrofitServiceClientBuilder<API> maxRetries(int retries) {
        configuration.setRetries(retries);
        return this;
    }

    /**
     * Sets the connection settings.
     *
     * @param connectionSettings the connection settings
     * @return this builder
     */
    public RetrofitServiceClientBuilder<API> connectionSettings(ConnectionSettings connectionSettings) {
        configuration.setConnectionSettings(connectionSettings);
        return this;
    }

    /**
     * Sets the meter registry to be used to register metrics. Metrics are not reported unless this is set.
     *
     * @param meterRegistry the meter registry to use to register metrics
     * @return this builder
     */
    public RetrofitServiceClientBuilder<API> metrics(MeterRegistry meterRegistry) {
        checkArgument(meterRegistry != null, "Meter registry must be non-null.");
        this.configuration.setMeterRegistry(meterRegistry);
        return this;
    }

    /**
     * Sets configuration for request tracking.
     *
     * @param requestTracking the request tracking configuration
     * @return this builder
     */
    public RetrofitServiceClientBuilder<API> requestTracking(RequestTracking requestTracking) {
        checkArgument(requestTracking != null, "Request tracking configuration must be non-null.");
        this.configuration.setRequestTracking(requestTracking);
        return this;
    }

    /**
     * Sets a custom {@link ObjectMapper} to be used for serializing request and deserializing response.
     * The default one is configured for JSON messaging with the followings:
     * <ul>
     *     <li>registered java time and jdk8 module</li>
     *     <li>dates are serialized as text (defaults to {@code yyyy-MM-dd'T'HH:mm:ss.SSSX}</li>
     *     <li>empty beans are permitted</li>
     *     <li>nulls are not serialized</li>
     *     <li>fields are used directly to serialize objects</li>
     * </ul>
     *
     * @param objectMapper the object mapper
     * @return this builder
     */
    public RetrofitServiceClientBuilder<API> objectMapper(ObjectMapper objectMapper) {
        checkArgument(objectMapper != null, "Object mapper must be non-null.");
        this.objectMapper = objectMapper;
        return this;
    }

    /**
     * Sets the SSL context configuration to enable custom secure HTTPS connections.
     *
     * @param sslContextConfiguration the SSL context configuration
     * @return this builder
     */
    public RetrofitServiceClientBuilder<API> customSSLContext(SSLContextConfiguration sslContextConfiguration) {
        checkArgument(sslContextConfiguration != null, "SSLContextConfiguration must be non-null.");
        this.configuration.setSslContextConfiguration(sslContextConfiguration);
        return this;
    }

    /**
     * Enables logging of HTTP call's events. The type of events logged depend on the client type being used. Defaults to disabled.
     * <p>
     * For OkHTTP client a single aggregated entry, similar to the following is logged:
     * <br>
     * {@code INFO  c.h.m.http.client.ServiceEndpoint http://localhost:53108/delay/400 httpEvents callFailed=299 callStart=0 canceled=297 connectEnd=28 connectStart=23 connectionAcquired=30 connectionReleased=299 dnsEnd=19 dnsStart=18 proxySelectEnd=18 proxySelectStart=18 requestHeadersEnd=33 requestHeadersStart=32 responseFailed=297}
     * <p>
     * For Netty client the individual events are logged.
     * <br>
     * {@code INFO  c.h.m.http.client.ServiceEndpoint channel - dataSent remoteAddress=localhost:53108 bytes=146}
     * <br>
     * {@code c.h.m.http.client.ServiceEndpoint channel - connectTime remoteAddress=localhost/127.0.0.1:53108 status=SUCCESS durationMs=2}
     *
     * @return this builder
     */
    public RetrofitServiceClientBuilder<API> logHttpEvents() {
        this.configuration.setLogHttpEvents(true);
        return this;
    }

    /**
     * Enables reporting of HTTP call's events as metrics. Defaults to disabled.
     * <br>
     * The actual metrics being reported differs by HTTP client type.
     * <p>
     * For OkHTTP client both the timings (under {@code http_client_request_trace_timing}) and durations (under {@code http_client_request_trace_duration}) are reported.
     * <p>
     * For Netty client only the durations are reported under {@code http_client_request_trace_duration}.
     *
     * @return this builder
     * @see com.hotels.molten.http.client.listener.HttpMetricsReporterHandler for OkHTTP timing metrics details
     * @see com.hotels.molten.http.client.listener.DurationMetricsReporterHandler for OkHTTP duration metrics details
     * @see com.hotels.molten.http.client.metrics.MicrometerHttpClientMetricsRecorder for Netty duration metrics details
     */
    public RetrofitServiceClientBuilder<API> reportHttpEvents() {
        this.configuration.setReportHttpEvents(true);
        return this;
    }
    /**
     * Sets configuration http protocol. If not set default protocols of OkHttpClient/HttpClient will be used.
     *
     * @param protocol the (@code Protocol} to set.
     * @return this builder
     */
    public RetrofitServiceClientBuilder<API> protocol(List<Protocol> protocol) {
        this.configuration.setProtocol(protocol);
        return this;
    }

    /**
     * Sets protobuf to be used for serializing request and deserializing response.
     *
     * @return this builder
     */
    public RetrofitServiceClientBuilder<API> useProtobuf() {
        this.useProtobuf = true;
        return this;
    }

    /**
     * Sets the {@code healthIndicatorWatcher} to watch health indicators of the client with.
     * <br>
     * The watcher will get notifications of the embedded circuitbreaker's health using a {@link com.hotels.molten.healthcheck.resilience4j.HealthIndicatorOverCircuitBreaker}.
     *
     * @param healthIndicatorWatcher the {@code healthIndicatorWatcher} to set
     * @return a reference to this Builder
     */
    public RetrofitServiceClientBuilder<API> healthIndicatorWatcher(HealthIndicatorWatcher healthIndicatorWatcher) {
        checkArgument(healthIndicatorWatcher != null, "Health indicator watcher must be non-null.");
        this.healthIndicatorWatcher = healthIndicatorWatcher;
        return this;
    }

    /**
     * Sets a custom predicate to decide which response should be handled as successful response.
     * <p>
     * By default, only a response with 2xx response code is successful.
     * <p>
     * <strong>WARN:</strong> only available if the return type of the API uses {@link Response}, for example {@code Mono<Response<CustomBodyObject>>}.
     *
     * @param successfulResponsePredicate predicates if the response if successful
     * @return this builder
     * @see HttpServiceInvocationExceptionHandlingReactiveProxyFactory
     * @see HttpExceptionBasedServiceInvocationExceptionFactory
     */
    public RetrofitServiceClientBuilder<API> responseSuccessfulWhen(Predicate<Response<?>> successfulResponsePredicate) {
        this.failedResponsePredicate = successfulResponsePredicate.negate();
        return this;
    }

    /**
     * Creates the client for the service API based on the current settings.
     *
     * @return the client, never null
     */
    public API buildClient() {
        ExpectedLoad expectedLoad = configuration.getExpectedLoad();
        long totalTimeoutInMs = configuration.getTotalTimeoutInMs();
        int concurrency = configuration.getConcurrency();
        String clientId = getUniqueClientId();
        LoggerFactory.getLogger(this.getClass())
            .info("Creating {} client for {} (clientId={}) with timeout={} concurrency={}", getHttpClientType(), api, clientId, totalTimeoutInMs, concurrency);
        if (configuration.getMeterRegistry() != null) {
            new RetrofitServiceClientConfigurationReporter(configuration.getMeterRegistry(), clientId).registerMetrics(configuration);
        }

        okhttp3.Call.Factory callFactory = createCallFactory(clientId);
        Builder retrofitBuilder = new Builder()
            .baseUrl(baseUrl)
            .callFactory(callFactory)
            .addCallAdapterFactory(ForbiddenResultReactorCallAdapterFactoryDecorator.decorate(ReactorCallAdapterFactory.createAsync()));
        if (useProtobuf) {
            retrofitBuilder.addConverterFactory(ProtoConverterFactory.create());
        } else {
            retrofitBuilder.addConverterFactory(JacksonConverterFactory.create(objectMapper));
        }
        Retrofit retrofit = retrofitBuilder.build();

        API service = retrofit.create(api);
        service = new ComputationSchedulerReactiveProxyFactory().wrap(api, service);
        service = makeItHideHttpLayer(service);
        service = addInstrumentation(clientId, service, "raw");
        service = addTimeout(service, expectedLoad.getPeakResponseTime(), clientId, "peak");
        service = makeItRetrying(service, clientId);
        service = addTimeout(service, Duration.ofMillis(totalTimeoutInMs), clientId, "total");
        service = addCircuitBreaker(service, clientId);
        service = addBulkhead(service, clientId, concurrency);
        service = new CallLoggingReactiveProxyFactory(clientId).wrap(api, service);
        service = addInstrumentation(clientId, service, "total");
        return service;
    }

    private okhttp3.Call.Factory createCallFactory(String clientId) {
        CallFactoryFactory factory = getHttpClientType() == HttpClientType.OKHTTP ? new OkHttpCallFactoryFactory() : new ReactorNettyCallFactoryFactory();
        return factory.createCallFactory(configuration, clientId);
    }

    private HttpClientType getHttpClientType() {
        return Optional.ofNullable(System.getProperty(SYSTEM_PROP_PREFIX + "TYPE_" + configuration.getNormalizedCanonicalApi()))
            .map(HttpClientType::valueOf)
            .orElse(Optional.ofNullable(System.getProperty(SYSTEM_PROP_PREFIX + "DEFAULT_TYPE"))
                .map(HttpClientType::valueOf)
                .orElse(HttpClientType.OKHTTP));
    }

    private String normalize(String canonicalName) {
        return canonicalName.replaceAll("\\.", "_");
    }

    private String getUniqueClientId() {
        String id = normalize(Optional.ofNullable(customGroupId).orElse(api.getCanonicalName()));
        checkState(USED_CLIENT_IDS.add(id), "The client ID " + id + " cannot be reused.");
        return id;
    }

    private API makeItHideHttpLayer(API service) {
        return new HttpServiceInvocationExceptionHandlingReactiveProxyFactory(failedResponsePredicate).wrap(api, service);
    }

    private API addInstrumentation(String clientId, API service, String qualifier) {
        API wrappedService = service;
        if (configuration.getMeterRegistry() != null) {
            wrappedService = new InstrumentedReactiveProxyFactory(configuration.getMeterRegistry(), clientId, qualifier).wrap(api, wrappedService);
        }
        return wrappedService;
    }

    private API addTimeout(API service, Duration timeout, String clientId, String metricsTypeTagValue) {
        TimeoutReactiveProxyFactory factory = new TimeoutReactiveProxyFactory(timeout);
        if (configuration.getMeterRegistry() != null) {
            factory.withMetrics(configuration.getMeterRegistry(), clientId, metricsTypeTagValue);
        }
        return factory.wrap(api, service);
    }

    private API makeItRetrying(API service, String clientId) {
        API retryingService = service;
        if (configuration.getRetries() > 0) {
            RetryingReactiveProxyFactory retryingReactiveProxyFactory = new RetryingReactiveProxyFactory(configuration.getRetries(), clientId);
            if (configuration.getMeterRegistry() != null) {
                retryingReactiveProxyFactory.withMetrics(configuration.getMeterRegistry());
            }
            retryingService = retryingReactiveProxyFactory.wrap(api, service);
        }
        return retryingService;
    }

    private API addCircuitBreaker(API service, String clientId) {
        var recoveryConfiguration = configuration.getRecoveryConfiguration();
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom()
            .failureRateThreshold(recoveryConfiguration.getFailureThreshold())
            .recordException(exception -> exception instanceof TemporaryServiceInvocationException)
            .waitDurationInOpenState(recoveryConfiguration.getResponsiveness())
            .writableStackTraceEnabled(false)
            .enableAutomaticTransitionFromOpenToHalfOpen()
            .minimumNumberOfCalls(configuration.getHealthyCircuitCallsToConsider())
            .permittedNumberOfCallsInHalfOpenState(configuration.getRecoveringCircuitCallsToConsider())
            .slowCallDurationThreshold(configuration.getSlowCallDurationThreshold())
            .slowCallRateThreshold(recoveryConfiguration.getSlowCallRateThreshold());
        if (recoveryConfiguration.getRecoveryMode() == RecoveryConfiguration.RecoveryMode.TIME_BASED) {
            builder.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize((int) recoveryConfiguration.getResponsiveness().toSeconds());
        } else {
            builder.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(configuration.getHealthyCircuitCallsToConsider());
        }
        CircuitBreakerConfig circuitBreakerConfig = builder.build();
        CompositeHealthIndicator compositeHealthIndicator = HealthIndicator.composite("http_client." + clientId);
        healthIndicatorWatcher.watch(compositeHealthIndicator);
        CircuitBreakingReactiveProxyFactory proxyFactory = new CircuitBreakingReactiveProxyFactory(clientId, circuitBreakerConfig, compositeHealthIndicator);
        if (configuration.getMeterRegistry() != null) {
            proxyFactory.setMeterRegistry(configuration.getMeterRegistry());
        }
        return proxyFactory.wrap(api, service);
    }

    private API addBulkhead(API service, String clientId, int concurrency) {
        BulkheadingReactiveProxyFactory proxy = new BulkheadingReactiveProxyFactory(
            BulkheadConfiguration.builder().isolationGroupName(clientId).maxConcurrency(concurrency).build());
        if (configuration.getMeterRegistry() != null) {
            proxy.setMeterRegistry(configuration.getMeterRegistry(), clientId);
        }
        return proxy.wrap(api, service);
    }

    private ObjectMapper defaultJsonObjectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(Include.NON_NULL)
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    private enum HttpClientType {
        OKHTTP,
        NETTY
    }
}
