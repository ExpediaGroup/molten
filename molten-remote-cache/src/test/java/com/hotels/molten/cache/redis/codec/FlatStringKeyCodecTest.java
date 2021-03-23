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

package com.hotels.molten.cache.redis.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.hotels.molten.cache.NamedCacheKey;

/**
 * Unit test for {@link FlatStringKeyCodec}.
 */
public class FlatStringKeyCodecTest {
    private FlatStringKeyCodec<Object, Object> codec;

    @BeforeEach
    public void initContext() {
        codec = new FlatStringKeyCodec<>();
    }

    static Object[][] keys() {
        return new Object[][] {
            new Object[] {"key", "key"},
            new Object[] {new ComplexTypeWithFlatKeySupport("top", new ComplexTypeWithFlatKeySupport.Nested(3, "sometext")), "top:3:sometext"},
            new Object[] {new ComplexType("top", new ComplexType.Nested(3, "sometext")), "top:3:sometext"},
            new Object[] {new ComplexType(null, new ComplexType.Nested(3, null)), "null:3:null"},
            new Object[] {new TypeWithCollections(ImmutableList.of(1, 2), ImmutableSet.of("a", "b"), ImmutableMap.of(1, "a", 2, "b"),
                ImmutableList.of(ImmutableSet.of(new TypeWithCollections.Nested("a", 1D), new TypeWithCollections.Nested("b", 2D)))), "1:2:a:b:1:a:2:b:a:1.0:b:2.0"},
            new Object[] {new NamedCacheKey<>("cachename", new ComplexType("top", new ComplexType.Nested(3, "sometext"))), "cachename:top:3:sometext"},
        };
    }

    @ParameterizedTest
    @MethodSource("keys")
    void should_encode_key_as_expected(Object key, String expectedFlatKey) {
        ByteBuffer encodedKey = codec.encodeKey(key);
        assertThat(StandardCharsets.UTF_8.decode(encodedKey).toString()).isEqualTo(expectedFlatKey);
    }

    @Test
    void should_throw_unsupported_for_encoding_value() {
        assertThatThrownBy(() -> codec.encodeValue(1))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
