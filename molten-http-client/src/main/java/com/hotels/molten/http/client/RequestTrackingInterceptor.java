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

import static java.util.Objects.requireNonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request.Builder;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hotels.molten.http.client.tracking.RequestTracking;

/**
 * Adds request tracking headers to request.
 */
final class RequestTrackingInterceptor implements Interceptor {
    private static final Logger LOG = LoggerFactory.getLogger(RequestTrackingInterceptor.class);
    private final RequestTracking requestTracking;

    RequestTrackingInterceptor(RequestTracking requestTracking) {
        this.requestTracking = requireNonNull(requestTracking);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        LOG.debug("Extending request with tracking headers.");
        Builder builder = chain.request().newBuilder();
        requestTracking.getClientIdSupplier().get().ifPresent(t -> builder.addHeader(t.getName(), t.getValue()));
        requestTracking.getSessionIdSupplier().get().ifPresent(t -> builder.addHeader(t.getName(), t.getValue()));
        requestTracking.getRequestIdSupplier().get().ifPresent(t -> builder.addHeader(t.getName(), t.getValue()));
        return chain.proceed(builder.build());
    }
}
