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

import java.util.Optional;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import com.hotels.molten.http.client.tracking.RequestTracking.TrackingHeader;

/**
 * Creates the default {@link RequestTracking} setup.
 * Uses provided client ID, messageGroupID, sessionID from MDC and set them as User-Agent, X-Message-Group-ID and X-Session-ID respectively.
 */
@RequiredArgsConstructor
public final class DefaultRequestTrackingFactory {
    @NonNull
    private final String clientId;

    /**
     * Creates the default {@link RequestTracking} configuration using the set client ID.
     *
     * @return the default request tracking
     */
    public RequestTracking createRequestTracking() {
        return RequestTracking.builder()
            .clientIdSupplier(() -> Optional.of(new TrackingHeader("User-Agent", clientId)))
            .requestIdSupplier(new MdcBasedMessageGroupIdTrackingHeaderSupplier("X-Message-Group-ID"))
            .sessionIdSupplier(new MdcBasedSessionIdTrackingHeaderSupplier("X-Session-ID"))
            .build();
    }
}
