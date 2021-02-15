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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

@Getter
@Slf4j
public final class HttpException extends RuntimeException {
    private final int statusCode;
    private final String message;
    private final Charset charset;
    private final byte[] errorBody;

    public HttpException(retrofit2.HttpException ex) {
        super(getMessage(ex), ex, false, false);
        statusCode = ex.code();
        errorBody = Optional.ofNullable(ex.response())
            .map(Response::errorBody)
            .map(errorBody -> {
                byte[] error;
                try {
                    error = errorBody.bytes();
                } catch (IOException e) {
                    LOG.warn("Error reading error body", e);
                    error = null;
                }
                return error;
            }).orElse(null);
        charset = Optional.ofNullable(ex.response())
            .map(Response::errorBody)
            .map(ResponseBody::contentType)
            .map(MediaType::charset)
            .orElse(StandardCharsets.UTF_8);
        message = errorBody == null
            ? super.getMessage()
            : super.getMessage() + " error_body=" + new String(errorBody, charset);
    }

    private static String getMessage(retrofit2.HttpException ex) {
        return "http_status=" + ex.code() + " error_message=" + ex.message();
    }
}
