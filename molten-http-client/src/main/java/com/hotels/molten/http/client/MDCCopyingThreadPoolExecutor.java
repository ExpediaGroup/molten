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

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.internal.Util;
import org.slf4j.MDC;

/**
 * Thread pool executor for OKHttp dispatcher copying MDC over threads.
 */
class MDCCopyingThreadPoolExecutor extends ThreadPoolExecutor {

    private static final int KEEP_ALIVE_TIME_IN_SECONDS = 60;

    MDCCopyingThreadPoolExecutor() {
        // this is the default executor configuration for okhttp3.Dispatcher
        super(0, Integer.MAX_VALUE, KEEP_ALIVE_TIME_IN_SECONDS, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp Dispatcher", false));
    }

    @Override
    public void execute(Runnable command) {
        super.execute(new MDCHolderRunnable(command));
    }

    private static final class MDCHolderRunnable implements Runnable {
        private final Runnable delegate;
        private final Map<String, String> mdcContextMap;

        private MDCHolderRunnable(Runnable delegate) {
            this.delegate = requireNonNull(delegate);
            mdcContextMap = MDC.getCopyOfContextMap();
        }

        @Override
        public void run() {
            if (mdcContextMap != null) {
                MDC.setContextMap(mdcContextMap);
            }
            try {
                delegate.run();
            } finally {
                MDC.clear();
            }
        }
    }
}
