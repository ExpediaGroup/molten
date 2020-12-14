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
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.Optional;

import org.slf4j.MDC;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.hotels.molten.http.client.tracking.RequestTracking.TrackingHeader;

/**
 * Unit test for {@link MdcBasedMessageGroupIdTrackingHeaderSupplier}.
 */
public class MdcBasedMessageGroupIdTrackingHeaderSupplierTest {
    private static final String HEADER_NAME = "header name";
    private static final String ID = "id";
    private MdcBasedMessageGroupIdTrackingHeaderSupplier provider;

    @BeforeMethod
    public void initContext() {
        initMocks(this);
        provider = new MdcBasedMessageGroupIdTrackingHeaderSupplier(HEADER_NAME);
    }

    @AfterMethod
    public void tearDownContext() {
        MDC.clear();
    }

    @Test
    public void shouldProvideMessageGroupIdFromMDC() {
        MDC.put("messageGroupId", ID);
        MDC.put("requestId", "something else");

        assertThat(provider.get(), is(Optional.of(new TrackingHeader(HEADER_NAME, ID))));
    }

    @Test
    public void shouldProvideRequestIdFromMDCIfMessageGroupIdIsNotAvailable() {
        MDC.put("requestId", ID);

        assertThat(provider.get(), is(Optional.of(new TrackingHeader(HEADER_NAME, ID))));
    }

    @Test
    public void shouldProvideEmptyIfSessionIdIsNotAvailable() {
        assertThat(provider.get(), is(Optional.empty()));
    }
}
