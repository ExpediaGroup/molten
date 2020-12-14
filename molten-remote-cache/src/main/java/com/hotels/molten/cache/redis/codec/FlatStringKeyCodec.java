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

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.primitives.Primitives;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.codec.ToByteBufEncoder;
import io.netty.buffer.ByteBuf;

/**
 * Redis codec to serialize keys as flat strings.
 * e.g. {@code some:cache:key}
 * <p/>
 * <strong>Only supports encoding keys.</strong>
 *
 * @param <K> the type of key to persist
 * @param <V> the type of value to persist
 */
public final class FlatStringKeyCodec<K, V> implements RedisCodec<K, V>, ToByteBufEncoder<K, V> {
    private final StringCodec codec;

    public FlatStringKeyCodec() {
        this(StandardCharsets.US_ASCII);
    }

    public FlatStringKeyCodec(Charset charset) {
        checkArgument(charset.name().equals("UTF-8") || charset.name().contains("ASCII"));
        this.codec = new StringCodec(charset);
    }

    @Override
    public ByteBuffer encodeKey(K key) {
        String flatKey = toFlatStringKey(key);
        return codec.encodeKey(flatKey);
    }

    @Override
    public void encodeKey(K key, ByteBuf target) {
        String flatKey = toFlatStringKey(key);
        codec.encodeKey(flatKey, target);
    }

    @Override
    public int estimateSize(Object keyOrValue) {
        return codec.estimateSize(keyOrValue);
    }

    @Override
    public K decodeKey(ByteBuffer bytes) {
        throw unsupported();
    }

    @Override
    public V decodeValue(ByteBuffer bytes) {
        throw unsupported();
    }

    @Override
    public ByteBuffer encodeValue(V value) {
        throw unsupported();
    }

    @Override
    public void encodeValue(V value, ByteBuf target) {
        throw unsupported();
    }

    private String toFlatStringKey(K key) {
        return key instanceof FlatKeyAware ? ((FlatKeyAware) key).asFlatKey() : convertToFlatValue(key);
    }

    private String convertToFlatValue(Object value) {
        String flatValue;
        if (value == null) {
            flatValue = "null";
        } else if (value instanceof String || value.getClass().isPrimitive() || Primitives.isWrapperType(value.getClass())) {
            flatValue = String.valueOf(value);
        } else if (Collection.class.isAssignableFrom(value.getClass())) {
            flatValue = ((Collection<Object>) value).stream().map(this::convertToFlatValue).collect(Collectors.joining(FlatKeyAware.SEPARATOR));
        } else if (Map.class.isAssignableFrom(value.getClass())) {
            flatValue = ((Map<Object, Object>) value).entrySet().stream()
                .map(e -> convertToFlatValue(e.getKey()) + FlatKeyAware.SEPARATOR + convertToFlatValue(e.getValue()))
                .collect(Collectors.joining(FlatKeyAware.SEPARATOR));
        } else {
            flatValue = convertToFlatObject(value);
        }
        return flatValue;
    }

    private String convertToFlatObject(Object obj) {
        return Arrays.stream(obj.getClass().getDeclaredFields())
            .filter(field -> !Modifier.isTransient(field.getModifiers()))
            .map(field -> {
                field.setAccessible(true);
                try {
                    return convertToFlatValue(field.get(obj));
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot extract key data from object=" + obj, e);
                }
            })
            .collect(Collectors.joining(FlatKeyAware.SEPARATOR));
    }

    private RuntimeException unsupported() {
        return new UnsupportedOperationException("This codec can only be used to encode keys.");
    }
}
