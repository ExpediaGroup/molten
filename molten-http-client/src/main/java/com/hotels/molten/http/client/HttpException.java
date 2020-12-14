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

import java.io.IOException;
import java.util.Optional;

import lombok.Getter;
import retrofit2.Response;

@Getter
public final class HttpException extends RuntimeException {
    private final int statusCode;

    public HttpException(retrofit2.HttpException ex) {
        super(getMessage(ex), ex, false, false);
        statusCode = ex.code();
    }

    private static String getMessage(retrofit2.HttpException ex) {
        StringBuilder msg = new StringBuilder("httpStatus=")
            .append(ex.code())
            .append(" error_message=")
            .append(ex.message());
        Optional.ofNullable(ex.response())
            .map(Response::errorBody)
            .map(errorBody -> {
                String error;
                try {
                    error = errorBody.string();
                } catch (IOException e) {
                    error = "error reading body";
                }
                return error;
            })
            .ifPresent(error -> msg.append(" error_body=").append(error));
        return msg.toString();
    }
}
