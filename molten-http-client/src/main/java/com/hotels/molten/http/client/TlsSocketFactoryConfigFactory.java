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

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Optional;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import com.google.common.io.Resources;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Factory that creates an initialized {@link SslSocketFactoryConfig} with TLS {@link SSLContext} from custom keystore.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TlsSocketFactoryConfigFactory {

    /**
     * Create an initialized {@link SslSocketFactoryConfig} using provided key store file parameters.
     * Uses default algorithms for instantiation of {@link KeyManagerFactory} and {@link TrustManagerFactory}.
     *
     * @param keyStoreFilePath the key store file path
     * @param keyStorePass     the key store password
     * @return the {@link SslSocketFactoryConfig} domain object
     */
    public static SslSocketFactoryConfig createConfig(String keyStoreFilePath, String keyStorePass) {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = readKeyStore(keyStoreFilePath, keyStorePass);
            trustManagerFactory.init(keyStore);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyStorePass.toCharArray());
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());
            Optional<KeyManager> keyManager = Arrays.stream(keyManagerFactory.getKeyManagers())
                .filter(X509KeyManager.class::isInstance)
                .findFirst();
            checkState(keyManager.isPresent(), "Couldn't find valid X509KeyManager");
            Optional<TrustManager> trustManager = Arrays.stream(trustManagerFactory.getTrustManagers())
                .filter(X509TrustManager.class::isInstance)
                .findFirst();
            checkState(trustManager.isPresent(), "Couldn't find valid X509TrustManager");
            return new SslSocketFactoryConfig(SSLContextConfiguration.builder()
                .trustManager((X509TrustManager) trustManager.get())
                .keyManager((X509KeyManager) keyManager.get())
                .build());
        } catch (Exception e) {
            throw e instanceof IllegalStateException ? (IllegalStateException) e : new IllegalStateException("Couldn't configure SSL context", e);
        }
    }

    private static KeyStore readKeyStore(String keyStoreFilePath, String keyStorePass) throws CertificateException, NoSuchAlgorithmException, KeyStoreException {
        try (InputStream keystoreStream = (InputStream) Resources.getResource(keyStoreFilePath).getContent()) {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(keystoreStream, keyStorePass.toCharArray());
            return ks;
        } catch (IllegalArgumentException | IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
