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

import java.time.Duration;

import org.slf4j.MDC;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import com.hotels.molten.core.MoltenCore;

/**
 * Integration test for {@link MoltenCore} with {@link MoltenMDC}.
 */
public class MoltenMDCTest {
    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String OTHER_VALUE = "othervalue";
    private static final String YET_ANOTHER_VALUE = "yet_another_value";

    @BeforeClass
    public void initContext() {
        MoltenCore.initialize();
    }

    @AfterMethod
    public void clearContext() {
        MoltenMDC.uninitialize();
    }

    @DataProvider(name = "onEachOperatorEnabled")
    public Object[][] getOnEachOperatorEnabled() {
        return new Object[][] {
            new Object[] {true},
            new Object[] {false}
        };
    }

    @Test(dataProvider = "onEachOperatorEnabled")
    public void should_propagate_MDC_when_subscribed_on_elastic(boolean onEachOperatorEnabled) {
        MoltenMDC.initialize(onEachOperatorEnabled);
        MDC.put(KEY, VALUE);
        StepVerifier.create(
            Mono.defer(() -> Mono.justOrEmpty(MDC.get(KEY)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(d -> d + MDC.get(KEY)))
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(VALUE + VALUE)
            .expectComplete()
            .verify();
        assertThat(MDC.get(KEY)).isEqualTo(VALUE);
    }

    @Test(dataProvider = "onEachOperatorEnabled")
    public void should_propagate_MDC_when_subscribed_on_parallel(boolean onEachOperatorEnabled) {
        MoltenMDC.initialize(onEachOperatorEnabled);
        MDC.put(KEY, VALUE);
        StepVerifier.create(
            Mono.defer(() -> Mono.justOrEmpty(MDC.get(KEY)))
                .subscribeOn(Schedulers.parallel())
                .map(d -> d + MDC.get(KEY)))
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(VALUE + VALUE)
            .expectComplete()
            .verify();
        assertThat(MDC.get(KEY)).isEqualTo(VALUE);
    }

    @Test(dataProvider = "onEachOperatorEnabled")
    public void should_propagate_MDC_when_publishing_on_elastic(boolean onEachOperatorEnabled) {
        MoltenMDC.initialize(onEachOperatorEnabled);
        MDC.put(KEY, VALUE);
        StepVerifier.create(
            Mono.justOrEmpty(MDC.get(KEY))
                .publishOn(Schedulers.boundedElastic())
                .map(d -> d + MDC.get(KEY)))
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(VALUE + VALUE)
            .expectComplete()
            .verify();
        assertThat(MDC.get(KEY)).isEqualTo(VALUE);
    }

    @Test(dataProvider = "onEachOperatorEnabled")
    public void should_propagate_MDC_when_publishing_on_parallel(boolean onEachOperatorEnabled) {
        MoltenMDC.initialize(onEachOperatorEnabled);
        MDC.put(KEY, VALUE);
        StepVerifier.create(
            Mono.justOrEmpty(MDC.get(KEY))
                .publishOn(Schedulers.parallel())
                .map(d -> d + MDC.get(KEY)))
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(VALUE + VALUE)
            .expectComplete()
            .verify();
        assertThat(MDC.get(KEY)).isEqualTo(VALUE);
    }

    @Test(dataProvider = "onEachOperatorEnabled")
    public void should_propagate_MDC_when_subscribed_on_single(boolean onEachOperatorEnabled) {
        MoltenMDC.initialize(onEachOperatorEnabled);
        MDC.put(KEY, VALUE);
        StepVerifier.create(
            Mono.defer(() -> Mono.justOrEmpty(MDC.get(KEY)))
                .subscribeOn(Schedulers.single())
                .map(d -> d + MDC.get(KEY)))
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(VALUE + VALUE)
            .expectComplete()
            .verify();
        assertThat(MDC.get(KEY)).isEqualTo(VALUE);
    }

    @Test(dataProvider = "onEachOperatorEnabled")
    public void should_propagate_MDC_when_subscribed_on_immediate(boolean onEachOperatorEnabled) {
        MoltenMDC.initialize(onEachOperatorEnabled);
        MDC.put(KEY, VALUE);
        StepVerifier.create(
            Mono.defer(() -> Mono.justOrEmpty(MDC.get(KEY)))
                .subscribeOn(Schedulers.immediate())
                .map(d -> d + MDC.get(KEY)))
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(VALUE + VALUE)
            .expectComplete()
            .verify();
        assertThat(MDC.get(KEY)).isEqualTo(VALUE);
    }

    @Test(dataProvider = "onEachOperatorEnabled")
    public void should_propagate_MDC_when_switching_schedulers(boolean onEachOperatorEnabled) {
        MoltenMDC.initialize(onEachOperatorEnabled);
        MDC.put(KEY, VALUE);
        StepVerifier.create(
            Mono.defer(() -> Mono.justOrEmpty(MDC.get(KEY)))
                .subscribeOn(Schedulers.parallel())
                .map(d -> d + MDC.get(KEY))
                .publishOn(Schedulers.parallel())
                .map(d -> d + MDC.get(KEY))
                .publishOn(Schedulers.single())
                .map(d -> d + MDC.get(KEY)))
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(VALUE + VALUE + VALUE + VALUE)
            .expectComplete()
            .verify();
        assertThat(MDC.get(KEY)).isEqualTo(VALUE);
    }

    @Test
    public void should_propagate_MDC_with_non_reactive_callback_with_transform() {
        MoltenMDC.initialize(false);
        MDC.put(KEY, VALUE);
        StepVerifier.create(
            Mono.defer(() -> Mono.justOrEmpty(VALUE))
                .doOnNext(i -> assertThat(MDC.get(KEY)).describedAs("before").isEqualTo(VALUE))
                .doOnNext(i -> MDC.put(KEY, OTHER_VALUE))
                .flatMap(i -> Mono.create(sink -> new Thread(() -> {
                    assertThat(MDC.get(KEY)).describedAs("non-reactive").isNull();
                    MDC.put(KEY, YET_ANOTHER_VALUE);
                    sink.success(i);
                }).start())
                    .doOnNext(k -> assertThat(MDC.get(KEY)).describedAs("inner-after").isEqualTo(YET_ANOTHER_VALUE))
                    .transform(MoltenCore.propagateContext())
                    .doOnNext(k -> assertThat(MDC.get(KEY)).describedAs("inner-after-propagate").isEqualTo(OTHER_VALUE))
                )
                .doOnNext(i -> assertThat(MDC.get(KEY)).describedAs("after").isEqualTo(OTHER_VALUE))
                .publishOn(Schedulers.parallel())
                .doOnNext(i -> assertThat(MDC.get(KEY)).describedAs("after-publish").isEqualTo(OTHER_VALUE))
                .flatMap(i -> Mono.justOrEmpty(MDC.get(KEY)))
        )
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(OTHER_VALUE)
            .expectComplete()
            .verify();
        assertThat(MDC.get(KEY)).isEqualTo(OTHER_VALUE); //since the first part of the above flow is on main thread
    }

    @Test
    public void should_maintain_assembly_time_MDC_value_with_non_reactive_callback_with_oneach_hook() {
        MoltenMDC.initialize(true);
        MDC.put(KEY, VALUE);
        StepVerifier.create(
            Mono.defer(() -> Mono.justOrEmpty(VALUE))
                .doOnNext(i -> assertThat(MDC.get(KEY)).describedAs("before").isEqualTo(VALUE))
                .doOnNext(i -> MDC.put(KEY, OTHER_VALUE))
                .flatMap(i -> Mono.create(sink -> new Thread(() -> {
                    assertThat(MDC.get(KEY)).describedAs("non-reactive").isNull();
                    MDC.put(KEY, YET_ANOTHER_VALUE);
                    sink.success(i);
                }).start())
                    .doOnNext(k -> assertThat(MDC.get(KEY)).describedAs("inner-after").isEqualTo(VALUE))
                )
                .doOnNext(i -> assertThat(MDC.get(KEY)).describedAs("after").isEqualTo(VALUE))
                .publishOn(Schedulers.parallel())
                .doOnNext(i -> assertThat(MDC.get(KEY)).describedAs("after-publish").isEqualTo(VALUE))
                .flatMap(i -> Mono.justOrEmpty(MDC.get(KEY)))
        )
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(VALUE)
            .expectComplete()
            .verify();
        assertThat(MDC.get(KEY)).isEqualTo(VALUE);
    }

    @Test(dataProvider = "onEachOperatorEnabled")
    public void should_keep_original_MDC_value_even_on_immediate_scheduler(boolean onEachOperatorEnabled) {
        MoltenMDC.initialize(onEachOperatorEnabled);
        MDC.put(KEY, VALUE);
        StepVerifier.create(
            Mono.defer(() -> Mono.justOrEmpty(MDC.get(KEY)))
                .subscribeOn(Schedulers.immediate()))
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(VALUE)
            .expectComplete()
            .verify();
        assertThat(MDC.get(KEY)).isEqualTo(VALUE);
    }
}
