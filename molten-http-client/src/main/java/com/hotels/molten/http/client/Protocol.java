package com.hotels.molten.http.client;
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import reactor.netty.http.HttpProtocol;

/**
 * The different configurable types of http protocols.
 */
@Getter
@AllArgsConstructor
public enum Protocol {

    /**
     * Cleartext HTTP/2 with no "upgrade" round trip.
     */
    HTTP_2C(HttpProtocol.H2C, okhttp3.Protocol.H2_PRIOR_KNOWLEDGE),
    /**
     * The IETF's binary-framed protocol that includes header compression.
     * HTTP/2 requires deployments of HTTP/2 that use TLS 1.2 support
     */
    HTTP_2(HttpProtocol.H2, okhttp3.Protocol.HTTP_2),
    /**
     * A plaintext framing that includes persistent connections.
     */
    HTTP_1_1(HttpProtocol.HTTP11, okhttp3.Protocol.HTTP_1_1);

    @NonNull
    private final HttpProtocol nettyProtocol;
    @NonNull
    private final okhttp3.Protocol okhttpProtocol;

}
