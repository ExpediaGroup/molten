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

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.jakewharton.retrofit2.adapter.reactor.Result;
import lombok.RequiredArgsConstructor;
import retrofit2.CallAdapter;
import retrofit2.Retrofit;

/**
 * Decorates {@link CallAdapter.Factory} to forbid the usage of {@link Result} response type.
 */
@RequiredArgsConstructor(staticName = "decorate")
public class ForbiddenResultReactorCallAdapterFactoryDecorator extends CallAdapter.Factory {
    private final CallAdapter.Factory decoratedFactory;

    @Nullable
    @Override
    public CallAdapter<?, ?> get(@Nonnull Type returnType, @Nonnull Annotation[] annotations, @Nonnull Retrofit retrofit) {
        if (returnType instanceof ParameterizedType) {
            Class<?> rawParameterType = getRawType(getParameterUpperBound(0, (ParameterizedType) returnType));
            if (rawParameterType == Result.class) {
                throw new IllegalStateException("Result as the return type of an API method is forbidden in molten. Please use retrofit2.Response instead.");
            }
        }
        return decoratedFactory.get(returnType, annotations, retrofit);
    }
}
