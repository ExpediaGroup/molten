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

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.LoggerFactory;

import com.hotels.molten.core.log.DelegatingLogger;
import com.hotels.molten.core.log.LogLevel;

/**
 * Handles invocation logging of a circuit-breaker wrapped method.
 */
final class CallLog {
    private static final DelegatingLogger LOGGER = new DelegatingLogger(LoggerFactory.getLogger(CallLog.class));
    private static final LogLevel SUCCESS_LEVEL = Optional.ofNullable(System.getProperty("MOLTEN_HTTP_CLIENT_CALL_LOG_SUCCESS_LEVEL"))
        .map(LogLevel::valueOf).orElse(LogLevel.DEBUG);
    private static final LogLevel RETRY_LEVEL = Optional.ofNullable(System.getProperty("MOLTEN_HTTP_CLIENT_CALL_LOG_RETRY_LEVEL"))
        .map(LogLevel::valueOf).orElse(LogLevel.INFO);
    private static final LogLevel ERROR_LEVEL = Optional.ofNullable(System.getProperty("MOLTEN_HTTP_CLIENT_CALL_LOG_FAILURE_LEVEL"))
        .map(LogLevel::valueOf).orElse(LogLevel.WARN);
    private final String groupId;
    private final Class<?> type;
    private final Method method;
    private long executionStartTime;
    private long executionEndTime;
    private int retry;

    CallLog(String groupId, Class<?> type, Method method) {
        this.groupId = requireNonNull(groupId);
        this.type = requireNonNull(type);
        this.method = requireNonNull(method);
    }

    void start() {
        executionStartTime = System.currentTimeMillis();
    }

    void succeed() {
        finish();
        logSuccess();
    }

    void fail(Throwable e) {
        finish();
        logError(e);
    }

    void failWithRetry(Throwable e, int retryIdx) {
        retry = retryIdx;
        finish();
        logRetry(e);
    }

    private void finish() {
        executionEndTime = System.currentTimeMillis();
    }

    private long getDuration() {
        return executionEndTime - executionStartTime;
    }

    private void logSuccess() {
        LOGGER.log(SUCCESS_LEVEL, "target={}#{} circuit={} duration={} result=SUCCESS", type.getName(), method.getName(), groupId, getDuration());
    }

    private void logRetry(Throwable e) {
        LOGGER.log(RETRY_LEVEL, "target={}#{} circuit={} duration={} result=RETRY shortCircuited={} rejected={} timedOut={} retry={} error={} {}",
            type.getName(), method.getName(), groupId, getDuration(), isShortCircuited(e), isRejected(e), isTimedOut(e),
            retry, causeOrSelf(e).getClass().getName(), causeOrSelf(e).getMessage());
    }

    private void logError(Throwable e) {
        LOGGER.log(ERROR_LEVEL, "target={}#{} circuit={} duration={} result=FAIL shortCircuited={} rejected={} timedOut={} retry={} error={} {}",
            type.getName(), method.getName(), groupId, getDuration(), isShortCircuited(e), isRejected(e), isTimedOut(e),
            retry, causeOrSelf(e).getClass().getName(), causeOrSelf(e).getMessage());
    }

    private boolean isShortCircuited(Throwable e) {
        return e.getCause() instanceof CallNotPermittedException;
    }

    private boolean isRejected(Throwable e) {
        return e.getCause() instanceof BulkheadFullException;
    }

    private boolean isTimedOut(Throwable e) {
        return e.getCause() instanceof TimeoutException;
    }

    private Throwable causeOrSelf(Throwable e) {
        return e.getCause() != null ? e.getCause() : e;
    }

    private String getMessage(Throwable ex) {
        return ex instanceof HttpException ? "httpStatus=" + ((HttpException) ex).getStatusCode() : ex.getMessage();
    }
}
