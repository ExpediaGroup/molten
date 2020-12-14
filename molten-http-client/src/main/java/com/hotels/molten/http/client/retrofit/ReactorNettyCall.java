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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import brave.Span;
import brave.http.HttpClientHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.Timeout;
import org.jetbrains.annotations.NotNull;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;

import com.hotels.molten.core.MoltenCore;
import com.hotels.molten.core.common.Experimental;

/**
 * OkHttp call using a Reactor Netty client.
 */
@Experimental
@RequiredArgsConstructor
public final class ReactorNettyCall implements Call {
    @NonNull
    private final Request request;
    @NonNull
    private final Supplier<HttpClient> clientSupplier;
    @Nullable
    private final HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> traceHandler;
    private volatile boolean executed;
    private volatile Disposable callSubscription;
    private volatile AtomicBoolean canceled = new AtomicBoolean();

    @Override
    public Request request() {
        return request;
    }

    @Override
    public Response execute() throws IOException {
        throw new UnsupportedOperationException("execute() is not supported");
    }

    @Override
    public void enqueue(okhttp3.Callback responseCallback) {
        Request alteredRequest = request;
        Span span = null;
        if (traceHandler != null) {
            HttpClientRequest tracedRequest = new HttpClientRequest(alteredRequest);
            span = traceHandler.handleSend(tracedRequest);
            alteredRequest = tracedRequest.build();
        }
        final Request finalRequest = alteredRequest;
        final HttpClient.RequestSender httpClient = clientSupplier.get()
            .headers(headers -> addHeaders(headers, finalRequest))
            .request(HttpMethod.valueOf(finalRequest.method()))
            .uri(finalRequest.url().uri().toString());
        final RequestBody requestBody = finalRequest.body();
        HttpClient.ResponseReceiver<?> finalClient = httpClient;
        if (requestBody != null) {
            //TODO investigate more efficient option to copy buffer org.springframework.http.client.reactive.ReactorClientHttpRequest#writeWith
            Mono<ByteBuf> bodySupplier = Mono.fromCallable(() -> {
                var buffer = new Buffer();
                requestBody.writeTo(buffer);
                var charset = Optional.ofNullable(requestBody.contentType()).map(MediaType::charset).orElse(StandardCharsets.UTF_8);
                var byteBuf = ByteBufAllocator.DEFAULT.buffer();
                byteBuf.writeCharSequence(buffer.readString(charset), charset);
                return byteBuf;
            });
            finalClient = httpClient.send(bodySupplier);
        }
        final Call thisCall = this;
        final Span currentSpan = span;
        callSubscription = finalClient
            .responseSingle((response, rawBody) -> toResponse(finalRequest, response, rawBody))
            .doOnEach(e -> {
                if (traceHandler != null && !e.isOnComplete()) {
                    traceHandler.handleReceive(HttpClientResponse.maybeFrom(e.get()), currentSpan);
                }
            })
            .transform(MoltenCore.propagateContext())
            .subscribe(response -> {
                try {
                    responseCallback.onResponse(thisCall, response);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }, t -> responseCallback.onFailure(thisCall, t instanceof IOException ? (IOException) t : new IOException(t)));
        executed = true;
    }

    private Mono<Response> toResponse(Request finalRequest, reactor.netty.http.client.HttpClientResponse response, ByteBufMono responseBody) {
        return responseBody.asByteArray().switchIfEmpty(Mono.just(new byte[0])).map(bodyAsBytes -> {
            Response.Builder builder = new Response.Builder();
            builder.request(finalRequest)
                .code(response.status().code())
                .message(response.status().reasonPhrase())
                .body(new ResponseBody() {
                    @Override
                    public MediaType contentType() {
                        return Optional.ofNullable(response.responseHeaders().get(HttpHeaderNames.CONTENT_TYPE))
                            .map(MediaType::parse)
                            .orElse(null);
                    }

                    @Override
                    public long contentLength() {
                        return bodyAsBytes.length;
                    }

                    @Override
                    public BufferedSource source() {
                        return new Buffer().write(bodyAsBytes);
                    }
                });
            response.responseHeaders().forEach(header -> builder.addHeader(header.getKey(), header.getValue()));
            try {
                builder.protocol(Protocol.get(response.version().protocolName()));
            } catch (IOException e) {
                builder.protocol(Protocol.HTTP_1_1);
            }
            return builder.build();
        });
    }

    private void addHeaders(HttpHeaders headers, @NonNull Request source) {
        source.headers().forEach(header -> headers.add(header.getFirst(), header.getSecond()));
        Optional.ofNullable(source.body())
            .map(RequestBody::contentType)
            .ifPresent(contentType -> headers.add(HttpHeaderNames.CONTENT_TYPE, contentType.toString()));
    }

    @Override
    public void cancel() {
        if (canceled.compareAndSet(false, true)) {
            callSubscription.dispose();
        }
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    @Override
    public boolean isCanceled() {
        return canceled.get();
    }

    @NotNull
    @Override
    public Timeout timeout() {
        // timeout is handled in a layer above retrofit
        return Timeout.NONE;
    }

    @Override
    public ReactorNettyCall clone() {
        return new ReactorNettyCall(request, clientSupplier, traceHandler);
    }
}
