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
package com.hotels.molten.core.common;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.Queue;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A queue which wraps a delegate queue and saves context information for each added element. The context is restored when the element is retrieved from the queue.
 *
 * @param <CONTEXT> the type of context to propagate
 */
@SuppressWarnings("unchecked")
@RequiredArgsConstructor
public abstract class ContextPropagatorQueue<CONTEXT> extends AbstractQueue<Object> {
    @NonNull
    private final Queue<Object> delegate;

    /**
     * Gets the context to be saved.
     *
     * @return the context, might be null
     */
    protected abstract CONTEXT retrieveContext();

    /**
     * Restore the saved context.
     *
     * @param context the context to restore, might be null
     */
    protected abstract void restoreContext(CONTEXT context);

    @Override
    public boolean offer(Object o) {
        return delegate.offer(new ElementWithContext<>(o, retrieveContext()));
    }

    @Override
    public Object poll() {
        return restoreContextAndGetElement(delegate.poll());
    }

    @Override
    public Object peek() {
        return restoreContextAndGetElement(delegate.peek());
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> iterator() {
        Iterator<?> iterator = delegate.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Object next() {
                return restoreContextAndGetElement(iterator.next());
            }
        };
    }

    private Object restoreContextAndGetElement(Object wrappedElement) {
        var element = wrappedElement;
        if (wrappedElement instanceof ContextPropagatorQueue.ElementWithContext) {
            var elementWithContext = (ElementWithContext<CONTEXT>) wrappedElement;
            restoreContext(elementWithContext.context);
            element = elementWithContext.element;
        }
        return element;
    }

    @RequiredArgsConstructor
    private static final class ElementWithContext<CONTEXT> {
        @NonNull
        private final Object element;
        private final CONTEXT context;
    }
}

