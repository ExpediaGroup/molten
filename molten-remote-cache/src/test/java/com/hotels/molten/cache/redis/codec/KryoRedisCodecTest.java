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

import java.nio.ByteBuffer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoPool;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link KryoRedisCodec}.
 */
@Slf4j
public class KryoRedisCodecTest {
    private KryoRedisCodec<Object, Object> codec;
    private Kryo kryo;

    @BeforeEach
    void initContext() {
        kryo = new Kryo();
        codec = new KryoRedisCodec<>(new KryoPool.Builder(() -> kryo).build());
    }

    @Test
    void should_deserialize_which_was_serialized() {
        ByteBuffer encodedKey = codec.encodeKey("key");
        ComplexType value = new ComplexType("text", new ComplexType.Nested(1, "txt"));
        ByteBuffer encodedValue = codec.encodeValue(value);

        assertThat(codec.decodeKey(encodedKey)).isInstanceOf(String.class).isEqualTo("key");
        assertThat(codec.decodeKey(encodedValue)).isInstanceOf(ComplexType.class).isEqualTo(value);
    }
}
