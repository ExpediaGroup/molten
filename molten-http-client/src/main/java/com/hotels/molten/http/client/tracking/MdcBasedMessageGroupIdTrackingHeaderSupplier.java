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

package com.hotels.molten.http.client.tracking;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import com.hotels.molten.http.client.tracking.RequestTracking.TrackingHeader;

/**
 * Gets the message group ID from {@link MDC} and provides it as a {@link TrackingHeader}.
 */
@Slf4j
public class MdcBasedMessageGroupIdTrackingHeaderSupplier implements Supplier<Optional<TrackingHeader>> {
    private final String name;

    public MdcBasedMessageGroupIdTrackingHeaderSupplier(String name) {
        this.name = requireNonNull(name);
    }

    @Override
    public Optional<TrackingHeader> get() {
        Optional<String> id = Stream.of("messageGroupId", "requestId")
            .map(MDC::get)
            .filter(Objects::nonNull)
            .map(Object::toString)
            .filter(v -> !Strings.isNullOrEmpty(v))
            .findFirst();
        if (id.isEmpty()) {
            LOG.debug("Couldn't find neither messageGroupId nor requestId in MDC");
        }
        return id.map(value -> new TrackingHeader(name, value));
    }
}
