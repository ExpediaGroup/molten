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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Test object which has methods returning with reactive types.
 */
interface ReactiveApi {
    Flux<String> getAll(int id);

    default Mono<String> getFirst(int id) {
        return getAll(id).take(1).single();
    }

    ReactiveApi self();
}
