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

package com.hotels.molten.core.mdc;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

import com.hotels.molten.core.common.AbstractNonFusingSubscription;

/**
 * A {@link CoreSubscriber} that propagates MDC.
 */
@Slf4j
final class MDCContextPropagatingSubscriber<T> extends AbstractNonFusingSubscription<T> {
    private static final String MDC_CONTEXT_KEY = "X-Molten-MDC";
    private final Context context;

    private MDCContextPropagatingSubscriber(CoreSubscriber<? super T> subscriber, Map<String, String> mdc) {
        super(subscriber);
        this.context = mdc != null ? subscriber.currentContext().put(MDC_CONTEXT_KEY, mdc) : subscriber.currentContext();
        LOG.trace("Current context={}", this.context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static CoreSubscriber decorate(CoreSubscriber sub) {
        var mdc = MDC.getCopyOfContextMap();
        return mdc == null ? sub : new MDCContextPropagatingSubscriber(sub, mdc);
    }

    @Override
    public Context currentContext() {
        return context;
    }

    @Override
    protected void doOnSubscribe() {
        runWithMDC(() -> this.subscriber.onSubscribe(this));
    }

    @Override
    public void request(long n) {
        runWithMDC(() -> this.subscription.request(n));
    }

    @Override
    public void cancel() {
        runWithMDC(() -> this.subscription.cancel());
    }

    @Override
    public void onNext(T o) {
        runWithMDC(() -> this.subscriber.onNext(o));
    }

    @Override
    public void onError(Throwable throwable) {
        runWithMDC(() -> this.subscriber.onError(throwable));
    }

    @Override
    public void onComplete() {
        runWithMDC(this.subscriber::onComplete);
    }

    private void runWithMDC(Runnable runnable) {
        var currentMDC = MDC.getCopyOfContextMap();
        try {
            this.context.<Map<String, String>>getOrEmpty(MDC_CONTEXT_KEY)
                .ifPresent(MDC::setContextMap);
            runnable.run();
        } finally {
            if (currentMDC != null) {
                MDC.setContextMap(currentMDC);
            } else {
                MDC.clear();
            }
        }
    }
}
