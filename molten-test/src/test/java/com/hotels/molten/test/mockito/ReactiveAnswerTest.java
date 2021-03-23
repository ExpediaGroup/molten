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

package com.hotels.molten.test.mockito;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit test for {@link ReactiveAnswer}.
 */
public class ReactiveAnswerTest {

    private ReactiveApi mock;

    @BeforeEach
    void initMock() {
        mock = mock(ReactiveApi.class, new ReactiveAnswer(invocation -> null));
    }

    @Test
    void should_support_mono() {
        StepVerifier.create(mock.getMono())
            .expectSubscription()
            .verifyComplete();
    }

    @Test
    void should_support_flux() {
        StepVerifier.create(mock.getFlux())
            .expectSubscription()
            .verifyComplete();
    }

    private interface ReactiveApi {

        Mono<String> getMono();

        Flux<String> getFlux();
    }
}
