# reactive-http-client

Provides a reactive HTTP client builder for service/client interaction.

Builds a fully functional resilient client over a reactive interface against an HTTP (HTTPS, HTTP/2) service endpoint. 

Assuming we have the following API interface:

```java
interface ServiceEndpoint {
    @retrofit2.http.GET("getpath/{id}")
    Mono<Response> getData(@Path("id") String id);

    @retrofit2.http.POST("postpath")
    Mono<Response> postData(@Body Request request);
    
    @retrofit2.http.DELETE("delete/{id}")
    Mono<Void> delete(@Path("id") String id);
}
``` 

Then building a full-fledged reactive and resilient HTTP client for it would be:

```java
ServiceEndpoint client = RetrofitServiceClientBuilder.createOver(ServiceEndpoint.class, BASE_URL).buildClient();
```

The built client will have the following features built-in:
* connection pooling
* circuit-breaker
* bulkhead
* metrics
* retry
* request-tracing
* call-log 

See [com.hotels.molten.http.client.RetrofitServiceClientBuilder](src/main/java/com/hotels/molten/http/client/RetrofitServiceClientBuilder.java) for more details.

## Configuration

The client built is highly configurable:

```java
ServiceEndpoint client = RetrofitServiceClientBuilder.createOver(ServiceEndpoint.class, BASE_URL)
    .expectedLoad(ExpectedLoad.builder()
        .peakResponseTime(Duration.ofMillis(800))
        .averageResponseTime(Duration.ofMillis(20))
        .peakRequestRatePerSecond(10)
        .averageRequestRatePerSecond(5)
        .build())          
    .recovery(RecoveryConfiguration.builder()
        .responsiveness(Duration.ofSeconds(10))
        .failureThreshold(25)
        .slowCallDurationThreshold(50)
        .slowCallRateThreshold(75)
        .build())
    .connectionSettings(ConnectionSettings.builder()
        .timeout(Duration.ofSeconds(1))
        .keepAliveIdle(Duration.ofMinutes(10))
        .maxLife(Duration.ofMinutes(15))
        .retryOnConnectionFailure(false)
        .build())
    .maxRetries(2)
    .objectMapper(customObjectMapper) // Jackson object mapper
    .metrics(meterRegistry) // micrometer registry
    .groupId("customGroupId") // isolates circuits, bulkheads, metrics and call logs
    .requestTracking(customRequestTracking) // to propagate certain data with every request
    .httpTracing(httpTracing) // Zipkin Brave http tracing
    .buildClient();
```

See default settings and additional details in [com.hotels.molten.http.client.RetrofitServiceClientBuilder](src/main/java/com/hotels/molten/http/client/RetrofitServiceClientBuilder.java) and [com.hotels.molten.http.client.RetrofitServiceClientConfiguration](src/main/java/com/hotels/molten/http/client/RetrofitServiceClientConfiguration.java).

### HTTP client implementation
The default underlying HTTP client being used is OkHTTP. 
Reactor-netty based HTTP client is also supported, however, it is still experimental.
To switch to reactor-netty client add it to classpath
```xml 
<dependency>
  <groupId>io.projectreactor.netty</groupId>
  <artifactId>reactor-netty</artifactId>
</dependency>
``` 
and enable it for selected or all clients via system properties:
- `MOLTEN_HTTP_CLIENT_DEFAULT_TYPE=[OKHTTP|NETTY]` to set client type globally
- `MOLTEN_HTTP_CLIENT_TYPE_fully_qualified_Api=[OKHTTP|NETTY]` to set client type for a specific client only (e.g. `fully.qualified.Api` in this case)

### Protobuf
The client supports Protobuf for serializing request and deserializing responses. This can be enabled using `builder.usingProtobuf()`.
Be sure to add protobuf as dependency:
```
<dependency>
  <groupId>com.google.protobuf</groupId>
  <artifactId>protobuf-java</artifactId>
</dependency>
```
 
## Metrics
If `meterRegistry` is set then several metrics are automatically registered along with the final configuration of the client.
See [com.hotels.molten.http.client.RetrofitServiceClientBuilder](src/main/java/com/hotels/molten/http/client/RetrofitServiceClientBuilder.java) documentation for details.

Preconfigured Grafana dashboards are available for:
 - [Graphite datasource](src/site/grafana/http_client_calls_graphite.json).
 - [Prometheus datasource](src/site/grafana/http_client_calls_prometheus.json).
 
See `com.hotels.molten.core.metrics.MetricsSupport` how to switch between hierarchical and dimensional metrics.
 
## Certificates
Custom HTTPS certificates are supported when `com.hotels.molten.http.client.SSLContextConfiguration` is provided.
See `com.hotels.molten.http.client.RetrofitServiceClientBuilder#customSSLContext` for details.
