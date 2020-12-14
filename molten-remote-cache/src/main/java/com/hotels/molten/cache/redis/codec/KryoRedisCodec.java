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

import static java.util.Objects.requireNonNull;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Optional;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.pool.KryoPool;
import io.lettuce.core.codec.RedisCodec;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis codec to serialize objects with Kryo.
 *
 * @param <K> the type of key to persist
 * @param <V> the type of value to persist
 */
@Slf4j
public final class KryoRedisCodec<K, V> implements RedisCodec<K, V> {
    private final KryoPool kryoPool;

    public KryoRedisCodec(KryoPool kryoPool) {
        this.kryoPool = requireNonNull(kryoPool);
    }

    @Override
    public ByteBuffer encodeKey(K key) {
        return encode(key, "key");
    }

    @Override
    public K decodeKey(ByteBuffer bytes) {
        return decode(bytes, "key");
    }

    @Override
    public ByteBuffer encodeValue(V value) {
        return encode(value, "value");
    }

    @Override
    public V decodeValue(ByteBuffer bytes) {
        return decode(bytes, "value");
    }

    private <T> ByteBuffer encode(T object, String type) {
        ByteBuffer bytes = null;
        try {
            bytes = toByteBuffer(object);
        } finally {
            if (LOG.isDebugEnabled()) {
                Optional<ByteBuffer> maybeBytes = Optional.ofNullable(bytes);
                LOG.debug("encoding {}:{} bytes of {}", type,
                    maybeBytes.map(b -> b.array().length).map(Object::toString).orElse("n/a"),
                    object.getClass());
                if (LOG.isTraceEnabled()) {
                    LOG.trace("encoded {}: {} to {}", type, object, maybeBytes.map(b -> Base64.getEncoder().encode(b.array())).orElse(null));
                }
            }
        }
        return bytes;
    }

    private <T> ByteBuffer toByteBuffer(T object) {
        return kryoPool.run(kryo -> {
            ByteBufferOutput output = new ByteBufferOutput(8192, -1);
            kryo.writeClassAndObject(output, object);
            output.close();
            return ByteBuffer.wrap(output.toBytes());
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T decode(ByteBuffer byteBuffer, String type) {
        T object = null;
        byte[] usedBytes = null;
        try {
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            object = kryoPool.run(kryo -> (T) kryo.readClassAndObject(new ByteBufferInput(bytes)));
            usedBytes = bytes;
        } finally {
            if (LOG.isDebugEnabled()) {
                LOG.debug("decoding {}:{} bytes as {}", type,
                    Optional.ofNullable(usedBytes).map(b -> b.length).map(Object::toString).orElse("n/a"),
                    Optional.ofNullable(object).map(Object::getClass).map(Class::toString).orElse("n/a"));
                if (LOG.isTraceEnabled()) {
                    LOG.trace("decoded {} from {} to {}", type,
                        Optional.ofNullable(usedBytes).map(b -> Base64.getEncoder().encode(b)).orElse(null), object);
                }
            }
        }
        return object;
    }
}
