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

import java.util.Optional;
import javax.annotation.Nullable;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SSLContextConfiguration {
    @Nullable
    private final String protocol;
    @Nullable
    private final X509TrustManager trustManager;
    @Nullable
    private final X509KeyManager keyManager;

    public Optional<String> getProtocol() {
        return Optional.ofNullable(protocol);
    }

    public Optional<X509TrustManager> getTrustManager() {
        return Optional.ofNullable(trustManager);
    }

    public Optional<X509KeyManager> getKeyManager() {
        return Optional.ofNullable(keyManager);
    }
}
