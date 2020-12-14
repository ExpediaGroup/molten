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

import static com.hotels.molten.http.client.Response.response;

import java.io.InputStream;
import java.net.ConnectException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.google.common.io.Resources;
import com.jakewharton.retrofit2.adapter.reactor.ReactorCallAdapterFactory;
import lombok.Value;
import okhttp3.OkHttpClient;
import org.testng.annotations.Test;
import reactor.test.StepVerifier;
import retrofit2.HttpException;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Verifies {@link StubJsonServiceServer} endpoints with a raw HTTP client built using OK HTTP and Retrofit.
 */
public class StubJsonServiceServerTest {
    private static final StubJsonServiceServer HTTP_SERVER = new StubJsonServiceServer();
    private static final StubJsonServiceServer HTTPS_SERVER = new StubJsonServiceServer();

    @Test
    public void testRawRetrofitClient() {
        HTTP_SERVER.start(true, false);
        ServiceEndpoint client = new Retrofit.Builder().baseUrl("http://localhost:" + HTTP_SERVER.getPort() + "/")
            .addConverterFactory(JacksonConverterFactory.create())
            .addCallAdapterFactory(ReactorCallAdapterFactory.createAsync())
            .build()
            .create(ServiceEndpoint.class);

        executeCallTests(client);
        HTTP_SERVER.shutDown();
        StepVerifier.create(client.getData("test")).verifyError(ConnectException.class);
    }

    @Test
    public void testRawTlsRetrofitClient() throws Exception {
        HTTPS_SERVER.start(true, true);

        SSLContextConfig sslContextConfig = createSSLConfig();
        ServiceEndpoint client = new Retrofit.Builder().baseUrl("https://localhost:" + HTTPS_SERVER.getPort() + "/")
            .client(new OkHttpClient().newBuilder()
                .sslSocketFactory(sslContextConfig.sslSocketFactory, sslContextConfig.trustManager)
                .build())
            .addConverterFactory(JacksonConverterFactory.create())
            .addCallAdapterFactory(ReactorCallAdapterFactory.createAsync())
            .build()
            .create(ServiceEndpoint.class);

        executeCallTests(client);
        HTTPS_SERVER.shutDown();
        StepVerifier.create(client.getData("test")).verifyError(ConnectException.class);
    }

    private void executeCallTests(ServiceEndpoint client) {
        StepVerifier.create(client.getData("test")).expectNext(response("test", 1)).verifyComplete();
        StepVerifier.create(client.getQuery("test")).expectNext(response("test", 1)).verifyComplete();
        StepVerifier.create(client.getDelayed(600).timeout(Duration.ofMillis(500))).verifyError(TimeoutException.class);
        StepVerifier.create(client.getDelayedWithFailure(600).timeout(Duration.ofMillis(500))).verifyError(TimeoutException.class);
        StepVerifier.create(client.getDelayedWithFailure(600)).verifyErrorMatches(ex -> ex instanceof HttpException && ((HttpException) ex).code() == 503);
        StepVerifier.create(client.getStatus(404)).verifyErrorMatches(ex -> ex instanceof HttpException && ((HttpException) ex).code() == 404);
        StepVerifier.create(client.getMaybe(3, 503)).verifyErrorMatches(ex -> ex instanceof HttpException && ((HttpException) ex).code() == 503);
        StepVerifier.create(client.getMaybe(3, 503)).verifyErrorMatches(ex -> ex instanceof HttpException && ((HttpException) ex).code() == 503);
        StepVerifier.create(client.getMaybe(3, 503)).expectNext(StubJsonServiceServer.SUCCESS).verifyComplete();
        StepVerifier.create(client.getHeader("CustomHeader", "value")).expectNext("value").verifyComplete();
        StepVerifier.create(client.pingHeader("deadbeef")).expectNextMatches(response -> Objects.equals(response.headers().get("pong"), "deadbeef")).verifyComplete();
    }

    private static SSLContextConfig createSSLConfig() throws Exception {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore keyStore = readKeyStore();
        trustManagerFactory.init(keyStore);
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "password".toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());
        Optional<TrustManager> trustManager = Arrays.stream(trustManagerFactory.getTrustManagers())
            .filter(X509TrustManager.class::isInstance)
            .findFirst();
        return new SSLContextConfig(sslContext.getSocketFactory(), (X509TrustManager) trustManager.get());
    }

    private static KeyStore readKeyStore() throws Exception {
        try (InputStream keystoreStream = (InputStream) Resources.getResource("certificate/testkeystore.jks").getContent()) {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(keystoreStream, "password".toCharArray());
            return ks;
        }
    }

    @Value
    private static class SSLContextConfig {
        private final SSLSocketFactory sslSocketFactory;
        private final X509TrustManager trustManager;
    }
}
