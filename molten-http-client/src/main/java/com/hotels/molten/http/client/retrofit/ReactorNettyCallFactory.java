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

package com.hotels.molten.http.client.retrofit;

import static java.util.Objects.requireNonNull;

import java.util.function.Supplier;
import javax.annotation.Nullable;

import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Request;
import reactor.netty.http.client.HttpClient;

import com.hotels.molten.core.common.Experimental;

/**
 * Call factory which creates {@link ReactorNettyCall}.
 */
@Experimental
@Slf4j
public class ReactorNettyCallFactory implements okhttp3.Call.Factory {
    private final Supplier<HttpClient> clientSupplier;
    @Nullable
    private final HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler;

    public ReactorNettyCallFactory(@NonNull Supplier<HttpClient> clientSupplier, @Nullable HttpTracing httpTracing) {
        this.clientSupplier = requireNonNull(clientSupplier);
        handler = httpTracing != null ? HttpClientHandler.create(httpTracing) : null;
    }

    @Override
    public Call newCall(Request request) {
        return new ReactorNettyCall(request, clientSupplier, handler);
    }
}
