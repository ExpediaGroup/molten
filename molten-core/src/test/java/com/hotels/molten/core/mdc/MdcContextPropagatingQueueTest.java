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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.LinkedBlockingQueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

/**
 * Unit test for {@link MdcContextPropagatingQueue}.
 */
@ExtendWith(MockitoExtension.class)
class MdcContextPropagatingQueueTest {
    public static final String KEY = "key";
    public static final String VALUE = "value";
    public static final String SOMETHING = "something";

    @BeforeEach
    @AfterEach
    void initContext() {
        MDC.clear();
    }

    @Test
    void should_propagate_mdc_with_element() {
        //Given
        var queue = new MdcContextPropagatingQueue(new LinkedBlockingQueue<>());
        MDC.put(KEY, VALUE);
        //When
        queue.offer(SOMETHING);
        MDC.clear();
        assertThat(MDC.getCopyOfContextMap()).isNull();
        assertThat(queue.poll()).isEqualTo(SOMETHING);
        //Then
        assertThat(MDC.get(KEY)).isEqualTo(VALUE);
    }
}
