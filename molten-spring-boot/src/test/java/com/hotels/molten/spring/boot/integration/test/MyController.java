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
package com.hotels.molten.spring.boot.integration.test;

import static com.hotels.molten.trace.TracingTransformer.span;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
public class MyController {
    @Inject
    private HelloApi apiClient;

    @GetMapping("/say-hello")
    public Mono<String> sayHello() {
        return apiClient.greet("Bob")
            .transform(span("around-client-call").forMono());
    }

    @PostMapping("/request-id")
    public Mono<String> requestId(@RequestBody Mono<String> body) {
        LOG.info("from controller");
        return Mono.fromCallable(() -> MDC.get("request-id"))
            .zipWith(body, (id, b) -> id)
            .flatMap(id -> apiClient.greet("Bob").thenReturn(id))
            .doFinally(s -> LOG.info("from controller mono"));
    }

    @PostMapping("/post-me")
    public Mono<String> postMe(@RequestBody Mono<String> body) {
        LOG.info("from controller");
        return body
            .map(b -> "got: " + b)
            .transform(span("around-body").forMono())
            .doFinally(s -> LOG.info("from controller mono"));
    }

    @GetMapping("/hello")
    public Mono<String> helloBob() {
        return Mono.just("\"Hello Bob!\"");
    }
}
