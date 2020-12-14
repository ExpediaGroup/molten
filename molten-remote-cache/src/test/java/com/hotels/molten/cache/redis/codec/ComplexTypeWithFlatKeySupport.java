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

import lombok.Value;

@Value
class ComplexTypeWithFlatKeySupport implements FlatKeyAware {
    private final String topLevelText;
    private final Nested nested;

    @Override
    public String asFlatKey() {
        return FlatKeyAware.toFlatKey(topLevelText, String.valueOf(nested.number), nested.text);
    }

    @Value
    static class Nested {
        private final int number;
        private final String text;
    }
}
