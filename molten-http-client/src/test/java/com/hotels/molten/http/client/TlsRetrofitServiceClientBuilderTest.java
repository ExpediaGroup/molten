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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.test.StepVerifier;

import com.hotels.molten.trace.test.AbstractTracingTest;

/**
 * Integration test for secure {@link RetrofitServiceClientBuilder}.
 */
public class TlsRetrofitServiceClientBuilderTest extends AbstractTracingTest {
    private static final AtomicInteger GRP_ID = new AtomicInteger();
    private static final StubJsonServiceServer SERVER = new StubJsonServiceServer();
    private static final String BASE_URL = "https://localhost:" + SERVER.getPort() + "/";

    @BeforeClass
    public void initServer() {
        SERVER.start(true, true);
        ServiceEndpoint warmupClient = defaultClientBuilder()
            .expectedLoad(ExpectedLoad.builder().peakResponseTime(Duration.ofMillis(2000)).peakRequestRatePerSecond(1).build())
            .connectionSettings(ConnectionSettings.builder().timeout(Duration.ofMillis(2000)).keepAliveIdle(Duration.ofSeconds(15)).build())
            .maxRetries(2)
            .buildClient();
        StepVerifier.create(warmupClient.getData("warmup").retry(1)).expectNext(response("warmup", 1)).verifyComplete();
    }

    @AfterClass
    public void tearDownContext() {
        SERVER.shutDown();
        System.clearProperty("MOLTEN_HTTP_CLIENT_TYPE_com_hotels_molten_http_client_ServiceEndpoint");
    }

    @DataProvider(name = "clientType")
    public Object[][] getClientType() {
        return new Object[][] {
            new Object[] {"NETTY"},
            new Object[] {"OKHTTP"}
        };
    }

    @Test(dataProvider = "clientType")
    public void shouldGetResponse(String clientType) {
        System.setProperty("MOLTEN_HTTP_CLIENT_TYPE_com_hotels_molten_http_client_ServiceEndpoint", clientType);
        ServiceEndpoint client = defaultClientBuilder().buildClient();
        StepVerifier.create(client.getData("test"))
            .thenAwait(Duration.ofMillis(5000))
            .expectNext(response("test", 1))
            .verifyComplete();
    }

    @Test(dataProvider = "clientType")
    public void shouldPost(String clientType) {
        System.setProperty("MOLTEN_HTTP_CLIENT_TYPE_com_hotels_molten_http_client_ServiceEndpoint", clientType);
        ServiceEndpoint client = defaultClientBuilder().buildClient();
        StepVerifier.create(client.postData(new Request("text", 13)))
            .thenAwait(Duration.ofMillis(5000))
            .expectNext(response("text", 13))
            .verifyComplete();
    }

    @Test(dataProvider = "clientType")
    public void shouldDelete(String clientType) {
        System.setProperty("MOLTEN_HTTP_CLIENT_TYPE_com_hotels_molten_http_client_ServiceEndpoint", clientType);
        ServiceEndpoint client = defaultClientBuilder().buildClient();
        StepVerifier.create(client.delete("test"))
            .thenAwait(Duration.ofMillis(5000))
            .verifyComplete();
    }

    private RetrofitServiceClientBuilder<ServiceEndpoint> defaultClientBuilder() {
        return RetrofitServiceClientBuilder.createOver(ServiceEndpoint.class, BASE_URL)
            .groupId("secure-grp" + GRP_ID.incrementAndGet())
            .customSSLContext(TlsSocketFactoryConfigFactory.createConfig("certificate/testkeystore.jks", "password").getSslContextConfiguration());
    }
}
