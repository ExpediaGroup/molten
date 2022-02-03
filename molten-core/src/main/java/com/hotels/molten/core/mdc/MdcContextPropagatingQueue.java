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
import java.util.Queue;

import lombok.NonNull;
import org.slf4j.MDC;

import com.hotels.molten.core.common.ContextPropagatorQueue;

/**
 * Propagates MDC context when adding and retrieving elements from queue.
 * Note that the MDC is cleared if there were no context saved when retrieving elements.
 */
public class MdcContextPropagatingQueue extends ContextPropagatorQueue<Map<String, String>> {

    public MdcContextPropagatingQueue(@NonNull Queue<Object> delegate) {
        super(delegate);
    }

    @Override
    protected Map<String, String> retrieveContext() {
        return MDC.getCopyOfContextMap();
    }

    @Override
    protected void restoreContext(Map<String, String> savedMdc) {
        if (savedMdc != null) {
            MDC.setContextMap(savedMdc);
        } else {
            MDC.clear();
        }
    }
}
