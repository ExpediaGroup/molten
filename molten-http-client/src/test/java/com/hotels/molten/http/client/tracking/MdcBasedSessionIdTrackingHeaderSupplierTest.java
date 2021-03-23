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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.hotels.molten.http.client.tracking.RequestTracking.TrackingHeader;

/**
 * Unit test for {@link MdcBasedSessionIdTrackingHeaderSupplier}.
 */
public class MdcBasedSessionIdTrackingHeaderSupplierTest {
    private static final String HEADER_NAME = "header name";
    private static final String ID = "id";

    private MdcBasedSessionIdTrackingHeaderSupplier provider;

    @BeforeEach
    public void initContext() {
        provider = new MdcBasedSessionIdTrackingHeaderSupplier(HEADER_NAME);
    }

    @AfterEach
    public void tearDownContext() {
        MDC.clear();
    }

    @Test
    public void should_provide_message_group_id_from_mdc() {
        MDC.put("sessionId", ID);

        assertThat(provider.get(), is(Optional.of(new TrackingHeader(HEADER_NAME, ID))));
    }

    @Test
    public void should_provide_empty_if_session_id_is_not_available() {
        assertThat(provider.get(), is(Optional.empty()));
    }
}
