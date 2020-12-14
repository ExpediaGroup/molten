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

package com.hotels.molten.http.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.testng.annotations.Test;

/**
 * Unit test for {@link TlsSocketFactoryConfigFactory}.
 */
public class TlsSocketFactoryConfigFactoryTest {
    private static final String KEY_STORE_FILE = "certificate/testkeystore.jks";
    private static final String VALID_PASS = "password";

    @Test
    public void shouldProvideAssembledConfigFromFactories() {
        assertThat(TlsSocketFactoryConfigFactory.createConfig(KEY_STORE_FILE, VALID_PASS)).hasNoNullFieldsOrProperties();
    }

    @Test
    public void shouldThrowExceptionIfCouldNotFindKeyStore() {
        assertThatThrownBy(() -> TlsSocketFactoryConfigFactory.createConfig("invalidKeyStoreFilePath", VALID_PASS))
            .isExactlyInstanceOf(IllegalStateException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldPropagateExceptionOccursDuringSslSocketFactoryConfigInstantiation() {
        assertThatThrownBy(() -> TlsSocketFactoryConfigFactory.createConfig(KEY_STORE_FILE, "invalidPass"))
            .isExactlyInstanceOf(IllegalStateException.class)
            .hasCauseInstanceOf(IOException.class);
    }

}
