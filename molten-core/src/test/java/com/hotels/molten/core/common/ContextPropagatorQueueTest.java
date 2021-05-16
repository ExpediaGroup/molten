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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import lombok.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link ContextPropagatorQueue}.
 */
@ExtendWith(MockitoExtension.class)
class ContextPropagatorQueueTest {
    public static final String ELEMENT = "element";
    public static final String ELEMENT_2 = "element_2";
    @Mock
    DummyContext context;
    @Mock
    DummyContextHandler contextHandler;
    DummyContextPropagatorQueue contextPropagatorQueue = new DummyContextPropagatorQueue(new LinkedBlockingQueue<>());

    @Test
    void should_save_and_restore_context_for_offer_and_poll() {
        when(contextHandler.retrieve()).thenReturn(context);
        contextPropagatorQueue.offer(ELEMENT);
        verify(contextHandler).retrieve();
        verifyNoMoreInteractions(contextHandler);
        assertThat(contextPropagatorQueue.poll()).isEqualTo(ELEMENT);
        verify(contextHandler).restore(context);
    }

    @Test
    void should_save_and_restore_context_for_offer_and_peek() {
        when(contextHandler.retrieve()).thenReturn(context);
        contextPropagatorQueue.offer(ELEMENT);
        verify(contextHandler).retrieve();
        verifyNoMoreInteractions(contextHandler);
        assertThat(contextPropagatorQueue.peek()).isEqualTo(ELEMENT);
        verify(contextHandler).restore(context);
        assertThat(contextPropagatorQueue.peek()).isEqualTo(ELEMENT);
        verify(contextHandler, times(2)).restore(context);
    }

    @Test
    void should_save_and_restore_context_for_offer_and_iterator() {
        when(contextHandler.retrieve()).thenReturn(context);
        contextPropagatorQueue.offer(ELEMENT);
        verify(contextHandler).retrieve();
        assertThat(contextPropagatorQueue.size()).isEqualTo(1);
        contextPropagatorQueue.offer(ELEMENT_2);
        verify(contextHandler, times(2)).retrieve();
        assertThat(contextPropagatorQueue.size()).isEqualTo(2);
        verifyNoMoreInteractions(contextHandler);
        var iterator = contextPropagatorQueue.iterator();
        assertThat(iterator.next()).isEqualTo(ELEMENT);
        verify(contextHandler).restore(context);
        assertThat(iterator.next()).isEqualTo(ELEMENT_2);
        verify(contextHandler, times(2)).restore(context);
    }

    class DummyContextPropagatorQueue extends ContextPropagatorQueue<DummyContext> {
        DummyContextPropagatorQueue(@NonNull Queue<Object> delegate) {
            super(delegate);
        }

        @Override
        protected DummyContext retrieveContext() {
            return contextHandler.retrieve();
        }

        @Override
        protected void restoreContext(DummyContext context) {
            contextHandler.restore(context);
        }
    }

    interface DummyContext {
    }

    interface DummyContextHandler {

        DummyContext retrieve();

        void restore(DummyContext context);
    }
}
