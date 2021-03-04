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
/**
 * Enums for protocols.
 */
public enum Protocols {
    /**
     * Cleartext HTTP/2 with no "upgrade" round trip.
     *
     */
    HTTP_2C,
    /**
     * The IETF's binary-framed protocol that includes header compression.
     * HTTP/2 requires deployments of HTTP/2 that use TLS 1.2 support
     */
    HTTP_2,
    /**
     * A plaintext framing that includes persistent connections.
     */
    HTTP_1_1,

}
