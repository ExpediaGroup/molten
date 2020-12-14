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

package com.hotels.molten.trace.test;

import java.util.Map;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import zipkin2.Span;

/**
 * A matcher matching span by its name.
 */
@Value
@Builder
public class SpanMatcher extends TypeSafeMatcher<Span> {
    private final String name;
    private final boolean hasParent;
    @Singular
    private final Map<String, String> tags;

    public static SpanMatcher spanWithName(String spanName) {
        return SpanMatcher.builder().name(spanName).hasParent(true).build();
    }

    public static SpanMatcher rootSpanWithName(String spanName) {
        return SpanMatcher.builder().name(spanName).hasParent(false).build();
    }

    @Override
    public boolean matchesSafely(Span span) {
        boolean matches = name.equals(span.name());
        if (hasParent) {
            matches &= span.parentId() != null;
        } else {
            matches &= span.parentId() == null;
        }
        matches &= span.tags().entrySet().containsAll(tags.entrySet());
        return matches;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("a");
        if (hasParent) {
            description.appendText(" nested");
        }
        description.appendText(" span with name ").appendValue(name);
        if (!tags.isEmpty()) {
            description.appendText(" and tags ").appendValue(tags);
        }
    }
}
