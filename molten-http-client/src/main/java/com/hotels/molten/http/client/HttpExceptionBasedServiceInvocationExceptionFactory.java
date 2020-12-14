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

import static java.util.Map.entry;

import java.util.Map;
import java.util.function.BiFunction;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Maps HTTP status codes to service invocation exception types.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class HttpExceptionBasedServiceInvocationExceptionFactory {
    private static final Map<Integer, BiFunction<Exception, Class<?>, ServiceInvocationException>> STATUS_CODE_TO_EXCEPTION_FACTORY;
    private static final BiFunction<Exception, Class<?>, ServiceInvocationException> PERMANENT_EXCEPTION_FACTORY = (cause, service) -> new PermanentServiceInvocationException(
        "Permanent service invocation exception", service, cause);
    private static final BiFunction<Exception, Class<?>, ServiceInvocationException> TEMPORARY_EXCEPTION_FACTORY = (cause, service) -> new TemporaryServiceInvocationException(
        "Temporary service invocation exception", service, cause);
    private static final BiFunction<Exception, Class<?>, ServiceInvocationException> DEFAULT_EXCEPTION_FACTORY = (cause, service) -> new TemporaryServiceInvocationException(
        "Unknown HTTP error", service, cause);

    static {
        STATUS_CODE_TO_EXCEPTION_FACTORY = Map.ofEntries(
            entry(300, PERMANENT_EXCEPTION_FACTORY), // multiple choices
            entry(301, PERMANENT_EXCEPTION_FACTORY), // moved permanently
            entry(302, TEMPORARY_EXCEPTION_FACTORY), // move temporarily
            entry(303, PERMANENT_EXCEPTION_FACTORY), // see other
            entry(304, TEMPORARY_EXCEPTION_FACTORY), // not modified
            entry(305, PERMANENT_EXCEPTION_FACTORY), // use proxy
            entry(307, TEMPORARY_EXCEPTION_FACTORY), // temporary redirect
            entry(308, TEMPORARY_EXCEPTION_FACTORY), // permanent redirect
            entry(400, PERMANENT_EXCEPTION_FACTORY), // bad request
            entry(401, PERMANENT_EXCEPTION_FACTORY), // unauthorized
            entry(402, PERMANENT_EXCEPTION_FACTORY), // payment required
            entry(403, PERMANENT_EXCEPTION_FACTORY), // forbidden
            entry(404, PERMANENT_EXCEPTION_FACTORY), // not found
            entry(405, PERMANENT_EXCEPTION_FACTORY), // method not allowed
            entry(406, PERMANENT_EXCEPTION_FACTORY), // not acceptable
            entry(407, PERMANENT_EXCEPTION_FACTORY), // proxy authentication required
            entry(408, TEMPORARY_EXCEPTION_FACTORY), // request timeout
            entry(409, PERMANENT_EXCEPTION_FACTORY), // conflict
            entry(410, PERMANENT_EXCEPTION_FACTORY), // gone
            entry(411, PERMANENT_EXCEPTION_FACTORY), // length required
            entry(412, PERMANENT_EXCEPTION_FACTORY), // precondition failed
            entry(413, PERMANENT_EXCEPTION_FACTORY), // request too long
            entry(414, PERMANENT_EXCEPTION_FACTORY), // request URI too long
            entry(415, PERMANENT_EXCEPTION_FACTORY), // unsupported media type
            entry(416, PERMANENT_EXCEPTION_FACTORY), // request range not satisfiable
            entry(417, PERMANENT_EXCEPTION_FACTORY), // expectation failed
            entry(418, PERMANENT_EXCEPTION_FACTORY), // I'm a teapot
            entry(419, PERMANENT_EXCEPTION_FACTORY), // insufficient space on resource
            entry(420, PERMANENT_EXCEPTION_FACTORY), // method failure
            entry(421, PERMANENT_EXCEPTION_FACTORY), // misdirected request
            entry(422, PERMANENT_EXCEPTION_FACTORY), // unprocessable entity
            entry(423, PERMANENT_EXCEPTION_FACTORY), // locked
            entry(424, PERMANENT_EXCEPTION_FACTORY), // failed dependency
            entry(426, PERMANENT_EXCEPTION_FACTORY), // upgrade required
            entry(428, PERMANENT_EXCEPTION_FACTORY), // precondition required
            entry(429, TEMPORARY_EXCEPTION_FACTORY), // too many requests
            entry(431, PERMANENT_EXCEPTION_FACTORY), // request header fields too large
            entry(451, PERMANENT_EXCEPTION_FACTORY), // unavailable for legal reasons
            entry(500, PERMANENT_EXCEPTION_FACTORY), // internal server error
            entry(501, PERMANENT_EXCEPTION_FACTORY), // not implemented
            entry(502, PERMANENT_EXCEPTION_FACTORY), // bad gateway
            entry(503, TEMPORARY_EXCEPTION_FACTORY), // service unavailable
            entry(504, TEMPORARY_EXCEPTION_FACTORY), // gateway timeout
            entry(505, PERMANENT_EXCEPTION_FACTORY), // HTTP version not supported
            entry(506, PERMANENT_EXCEPTION_FACTORY), // variant also negotiates
            entry(507, PERMANENT_EXCEPTION_FACTORY), // insufficient storage
            entry(508, PERMANENT_EXCEPTION_FACTORY), // loop detected
            entry(510, PERMANENT_EXCEPTION_FACTORY), // not extended
            entry(511, PERMANENT_EXCEPTION_FACTORY), // network authentication required
            entry(599, TEMPORARY_EXCEPTION_FACTORY)  // network connect timeout error
        );
    }

    static ServiceInvocationException createFrom(retrofit2.HttpException httpException, Class<?> service) {
        return STATUS_CODE_TO_EXCEPTION_FACTORY.getOrDefault(httpException.code(), DEFAULT_EXCEPTION_FACTORY).apply(new HttpException(httpException), service);
    }
}
