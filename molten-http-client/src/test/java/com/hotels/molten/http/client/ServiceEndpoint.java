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

import java.util.List;

import io.vertx.core.http.HttpVersion;
import reactor.core.publisher.Mono;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * API for {@link StubJsonServiceServer}.
 */
interface ServiceEndpoint {

    @GET("get/{id}")
    Mono<Response> getData(@Path("id") String id);

    @GET("getquery")
    Mono<Response> getQuery(@Query("id") String id);

    @GET("get/xml/{id}")
    Mono<Response> getDataAsXml(@Path("id") String id);

    @POST("post")
    Mono<Response> postData(@Body Request request);

    @GET("status/{code}")
    Mono<String> getStatus(@Path("code") Integer statusCode);

    @GET("delay/{ms}")
    Mono<String> getDelayed(@Path("ms") Integer ms);

    @GET("delaywithfailure/{ms}")
    Mono<String> getDelayedWithFailure(@Path("ms") Integer ms);

    @GET("maybe/{period}/{code}")
    Mono<String> getMaybe(@Path("period") Integer period, @Path("code") Integer code);

    @GET("header/{name}")
    Mono<String> getHeader(@Path("name") String name, @Header("CustomHeader") String customHeader);

    @GET("header/{name}")
    Mono<String> getHeaders(@Path("name") String name, @Header("CustomHeader") List<String> customHeader);

    @POST("contenttype")
    Mono<Response> contentType(@Body Request request);

    @GET("pingheader")
    Mono<retrofit2.Response<Response>> pingHeader(@Header("ping") String pingHeader);

    @DELETE("delete/{id}")
    Mono<Void> delete(@Path("id") String id);

    @GET("find/{id}")
    Mono<Response> find(@Path("id") String id);

    @GET("resetcounter")
    Mono<String> resetCounter();

    @GET("/checkProtocol/{http}")
    Mono<String> checkProtocol(@Path("http") HttpVersion http);
}
