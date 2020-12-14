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

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import okhttp3.Request;

@RequiredArgsConstructor
final class HttpClientRequest extends brave.http.HttpClientRequest {
    @NonNull
    private final Request delegate;
    private Request.Builder builder;

    @Override
    public Object unwrap() {
        return delegate;
    }

    @Override
    public String method() {
        return delegate.method();
    }

    @Override
    public String path() {
        return delegate.url().encodedPath();
    }

    @Override
    public String url() {
        return delegate.url().toString();
    }

    @Override
    public String header(String name) {
        return delegate.header(name);
    }

    @Override
    public void header(String name, String value) {
        if (builder == null) {
            builder = delegate.newBuilder();
        }
        builder.header(name, value);
    }

    Request build() {
        return builder != null ? builder.build() : delegate;
    }
}
