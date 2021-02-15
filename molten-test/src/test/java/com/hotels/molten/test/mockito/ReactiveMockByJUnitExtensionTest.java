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

package com.hotels.molten.test.mockito;

import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link ReactiveMock} initiated by {@link MockitoExtension}.
 */
@ExtendWith(MockitoExtension.class)
public class ReactiveMockByJUnitExtensionTest {
    @ReactiveMock
    private ReactiveApi legacyReactiveMock;
    @ReactiveMock(serializable = true, stubOnly = true, extraInterfaces = Function.class, name = "custom legacy mock")
    private ReactiveApi customLegacyReactiveMock;
    @Mock
    private ReactiveApi reactiveMock;
    @Mock(answer = Answers.CALLS_REAL_METHODS, name = "custom mock")
    private ReactiveApi callsRealReactiveMock;

    static Stream<MockHolder> mocks() {
        return Stream.of(
            test -> test.legacyReactiveMock,
            test -> test.customLegacyReactiveMock,
            test -> test.reactiveMock,
            test -> test.callsRealReactiveMock
        );
    }

    static Stream<MockHolder> callRealMocks() {
        return Stream.of(
            test -> test.legacyReactiveMock,
            test -> test.customLegacyReactiveMock,
            test -> test.callsRealReactiveMock
        );
    }

    @ParameterizedTest
    @MethodSource("mocks")
    void shouldEmitStubValue(MockHolder mockHolder) {
        ReactiveMockTestCases.shouldEmitStubValue(mockHolder.extract(this));
    }

    @ParameterizedTest
    @MethodSource("mocks")
    void shouldEmitEmptyByDefault(MockHolder mockHolder) {
        ReactiveMockTestCases.shouldEmitEmptyByDefault(mockHolder.extract(this));
    }

    @ParameterizedTest
    @MethodSource("callRealMocks")
    void shouldSupportDefaultMethod(MockHolder mockHolder) {
        ReactiveMockTestCases.shouldSupportDefaultMethod(mockHolder.extract(this));
    }

    @ParameterizedTest
    @MethodSource("mocks")
    void shouldSupportMockitoAnnotationProperties(MockHolder mockHolder) {
        ReactiveMockTestCases.shouldSupportMockitoAnnotationProperties(mockHolder.extract(this));
    }

    private interface MockHolder {
        ReactiveApi extract(ReactiveMockByJUnitExtensionTest test);
    }
}
