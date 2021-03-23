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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * The supported HTTP protocols. A client may be set to use only a set of them.
 *
 * @see RetrofitServiceClientBuilder#useProtocols
 */
@Getter
@AllArgsConstructor
public enum HttpProtocol {

    /**
     * HTTP/2.0 support with clear-text.
     * See client specific docs for more details.
     *
     * @see reactor.netty.http.HttpProtocol#H2C
     * @see okhttp3.Protocol#H2_PRIOR_KNOWLEDGE
     */
    HTTP_2C(reactor.netty.http.HttpProtocol.H2C, okhttp3.Protocol.H2_PRIOR_KNOWLEDGE),
    /**
     * HTTP/2.0 support with TLS.
     * See client specific docs for more details.
     *
     * @see reactor.netty.http.HttpProtocol#H2
     * @see okhttp3.Protocol#HTTP_2
     */
    HTTP_2(reactor.netty.http.HttpProtocol.H2, okhttp3.Protocol.HTTP_2),
    /**
     * HTTP/1.1. The default supported HTTP protocol.
     * See client specific docs for more details.
     *
     * @see reactor.netty.http.HttpProtocol#HTTP11
     * @see okhttp3.Protocol#HTTP_1_1
     */
    HTTP_1_1(reactor.netty.http.HttpProtocol.HTTP11, okhttp3.Protocol.HTTP_1_1);

    @NonNull
    private final reactor.netty.http.HttpProtocol nettyProtocol;
    @NonNull
    private final okhttp3.Protocol okhttpProtocol;

}
