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

package com.hotels.molten.cache;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit test for {@link ReactiveCaffeineCache}.
 */
@ExtendWith(MockitoExtension.class)
public class ReactiveCaffeineCacheTest implements ReactiveCacheTestContract {
    private static final String VALUE = "one";
    private static final int KEY = 1;
    @Mock
    private Cache<Integer, String> cache;
    @InjectMocks
    private ReactiveCaffeineCache<Integer, String> reactiveCache;

    @Override
    public <T> ReactiveCache<Integer, T> createCacheForContractTest() {
        return new ReactiveCaffeineCache<>(Caffeine.newBuilder().build());
    }

    @Test
    public void should_delegate_get_lazily() {
        when(cache.getIfPresent(KEY)).thenReturn(VALUE);
        Mono<String> get = reactiveCache.get(KEY);
        verifyNoInteractions(cache);
        StepVerifier.create(get)
            .expectSubscription()
            .thenRequest(1)
            .then(() -> verify(cache).getIfPresent(KEY))
            .expectNext(VALUE)
            .verifyComplete();
    }

    @Test
    public void should_delegate_put_lazily() {
        Mono<Void> put = reactiveCache.put(KEY, VALUE);
        verifyNoInteractions(cache);
        StepVerifier.create(put)
            .expectSubscription()
            .thenRequest(1)
            .then(() -> verify(cache).put(KEY, VALUE))
            .verifyComplete();
    }
}
