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

package com.hotels.molten.test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.hamcrest.MatcherAssert;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.publisher.Operators;

/**
 * A test subscriber to assert states of the subscription.
 *
 * @param <T> the type of value
 */
@Slf4j
public class AssertSubscriber<T> implements Subscriber<T>, Disposable {
    private static final Duration MAX_WAIT_TIME = Duration.ofSeconds(3);
    private static final AtomicReferenceFieldUpdater<AssertSubscriber, Subscription> S =
        AtomicReferenceFieldUpdater.newUpdater(AssertSubscriber.class,
            Subscription.class,
            "subscription");
    private final CountDownLatch terminated = new CountDownLatch(1);
    private final AtomicBoolean subscribed = new AtomicBoolean();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final List<T> values = new CopyOnWriteArrayList<>();
    private volatile Subscription subscription;

    @Override
    public void onSubscribe(Subscription s) {
        MatcherAssert.assertThat("Should not be subscribed", subscribed.compareAndSet(false, true));
        s.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T value) {
        values.add(value);
    }

    @Override
    public void onError(Throwable t) {
        MatcherAssert.assertThat("Error should happen once", error.compareAndSet(null, t));
        terminated.countDown();
    }

    @Override
    public void onComplete() {
        MatcherAssert.assertThat("Complete should happen once", completed.compareAndSet(false, true));
        terminated.countDown();
    }

    @Override
    public boolean isDisposed() {
        return subscription == Operators.cancelledSubscription();
    }

    @Override
    public void dispose() {
        Subscription s = S.getAndSet(this, Operators.cancelledSubscription());
        if (s != null && s != Operators.cancelledSubscription()) {
            s.cancel();
            terminated.countDown();
        }
    }

    /**
     * Creates a new subscriber for assertions.
     *
     * @param <T> the element type
     * @return the subscriber
     */
    public static <T> AssertSubscriber<T> create() {
        return new AssertSubscriber<>();
    }

    /**
     * Awaits termination up to 3 seconds.
     *
     * @return this
     */
    public AssertSubscriber<T> await() {
        return await(MAX_WAIT_TIME);
    }

    /**
     * Awaits termination up to max wait time.
     *
     * @param maxWaitTime the maximum time to wait
     * @return this
     */
    public AssertSubscriber<T> await(Duration maxWaitTime) {
        try {
            MatcherAssert.assertThat("No error or complete event happened in time", terminated.await(maxWaitTime.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Assertions.fail("Wait interrupted");
        }
        return this;
    }

    /**
     * Asserts that a complete signal has been received.
     *
     * @return this
     */
    public AssertSubscriber<T> assertComplete() {
        logErrorIfPresent();
        MatcherAssert.assertThat("Error happened " + error.get(), error.get() == null);
        MatcherAssert.assertThat("Not complete", completed.get());
        return this;
    }

    /**
     * Asserts that neither a complete nor an error signal has been received.
     *
     * @return this
     */
    public AssertSubscriber<T> assertNotTerminated() {
        logErrorIfPresent();
        MatcherAssert.assertThat("Terminated", terminated.getCount() != 0);
        return this;
    }

    /**
     * Asserts that no values has been received.
     *
     * @return this
     */
    public AssertSubscriber<T> assertNoValues() {
        Assertions.assertThat(values).isEmpty();
        return this;
    }

    /**
     * Asserts that the exactly the given values has been received in order and nothing else.
     *
     * @param valuesInOrder the values in their expected order
     * @return this
     */
    public AssertSubscriber<T> assertResult(T... valuesInOrder) {
        assertComplete();
        Assertions.assertThat(values).containsExactly(valuesInOrder);
        return this;
    }

    /**
     * Asserts a completion with the expected values.
     *
     * @param assertion an assertion for the values
     * @return this
     */
    public AssertSubscriber<T> assertResult(Consumer<List<T>> assertion) {
        assertComplete();
        assertion.accept(values);
        return this;
    }

    /**
     * Asserts the emitted values only.
     *
     * @param assertion an assertion for the values
     * @return this
     */
    public AssertSubscriber<T> assertValues(Consumer<List<T>> assertion) {
        assertion.accept(values);
        return this;
    }

    /**
     * Asserts that only one result was emitted and completed.
     *
     * @param assertion an assertion for the values
     * @return this
     */
    public AssertSubscriber<T> assertOneResult(Consumer<T> assertion) {
        assertComplete();
        Assertions.assertThat(values).hasSize(1);
        assertion.accept(values.get(0));
        return this;
    }

    /**
     * Asserts that the given error has been received and nothing else.
     *
     * @param expectedErrorType the type of the expected error
     * @return this
     */
    public AssertSubscriber<T> assertError(Class<? extends Throwable> expectedErrorType) {
        MatcherAssert.assertThat("Error not happened", error.get() != null);
        MatcherAssert.assertThat("Completed", !completed.get());
        Assertions.assertThat(error.get()).isInstanceOf(expectedErrorType);
        return this;
    }

    /**
     * Expect an error and assert it via assertion(s) provided as a {@link Consumer}.
     *
     * @param assertionConsumer the consumer that applies assertion(s) on the error
     * @return this
     */
    public AssertSubscriber<T> assertErrorSatisfies(Consumer<Throwable> assertionConsumer) {
        MatcherAssert.assertThat("Error not happened", error.get() != null);
        MatcherAssert.assertThat("Completed", !completed.get());
        assertionConsumer.accept(error.get());
        return this;
    }

    /**
     * Asserts that the given error has been received and nothing else.
     *
     * @param expectedError the expected error
     * @return this
     */
    public AssertSubscriber<T> assertError(Throwable expectedError) {
        MatcherAssert.assertThat("Error not happened", error.get() != null);
        MatcherAssert.assertThat("Completed", !completed.get());
        Assertions.assertThat(error.get()).isSameAs(expectedError);
        return this;
    }

    private void logErrorIfPresent() {
        if (error.get() != null) {
            LOG.error("Error happened", error.get());
        }
    }
}
