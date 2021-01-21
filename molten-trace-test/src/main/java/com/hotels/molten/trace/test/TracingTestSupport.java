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

package com.hotels.molten.trace.test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

import brave.Tracing;
import brave.context.slf4j.MDCScopeDecorator;
import brave.http.HttpTracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.CountingSampler;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.LoggerFactory;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.brave.AsyncZipkinSpanHandler;
import zipkin2.reporter.okhttp3.OkHttpSender;

/**
 * Support class for testing tracing.
 * Also supports sending traces to remote zipkin server.
 * This class is not thread-safe thus should be used with care.
 */
@Slf4j
public final class TracingTestSupport {
    private static final ConcurrentLinkedDeque<Span> CAPTURED_SPANS = new ConcurrentLinkedDeque<>();
    private static Tracing tracing;
    private static HttpTracing httpTracing;
    private static boolean remoteReporterPresent;
    private static Duration waitTimeForRemoteReport = Duration.ofSeconds(3);
    private String appName = "molten";
    private int samplingProbability = 100;
    private int sampleRate = -1;
    private boolean sampleOnlyHttp;
    private String baseUrl = "http://127.0.0.1";
    private Integer port = 9411;
    private boolean compressionEnabled;
    private int maxMessageSize = -1;
    private int maxRequests = -1;
    private int queuedMaxBytes = -1;
    private int queuedMaxSpans = 1000;
    private int closeTimeout = -1;
    private int messageTimeout = -1;

    public static void initialize(boolean remoteReporterEnabled) {
        //this initializes the singleton Tracing.currentTracer() and HttpTracing.current() instances
        var tracingSupport = new TracingTestSupport();
        tracing = tracingSupport.createTracing(remoteReporterEnabled);
        httpTracing = tracingSupport.createHttpTracing(tracing);
        remoteReporterPresent = remoteReporterEnabled;
    }

    public static void cleanUp() {
        if (remoteReporterPresent) {
            try {
                LOG.info("Waiting {} for remote reporter to finish...", waitTimeForRemoteReport);
                Thread.sleep(waitTimeForRemoteReport.toMillis());
            } catch (InterruptedException e) {
                // noone cares
            }
        }
        Optional.ofNullable(HttpTracing.current()).ifPresent(HttpTracing::close);
        Optional.ofNullable(Tracing.current()).ifPresent(Tracing::close);
    }

    public static void resetCapturedSpans() {
        CAPTURED_SPANS.clear();
    }

    public static List<Span> capturedSpans() {
        return List.copyOf(CAPTURED_SPANS);
    }

    public static Tracing tracing() {
        return tracing;
    }

    public static HttpTracing httpTracing() {
        return httpTracing;
    }

    private Tracing createTracing(boolean remoteReporterEnabled) {
        LOG.info("Initializing tracing with identifier={} remoteReporting={} sampleOnlyHttp={}", appName, remoteReporterEnabled, sampleOnlyHttp);
        return Tracing.newBuilder()
            .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
                .addScopeDecorator(MDCScopeDecorator.get())
                .build())
            .localServiceName(appName.toLowerCase())
            .sampler(getTracingSampler())
            .addSpanHandler(AsyncZipkinSpanHandler.create(createReporter(remoteReporterEnabled)))
            .traceId128Bit(true)
            .build();
    }

    private HttpTracing createHttpTracing(Tracing tracing) {
        return HttpTracing.create(tracing);
    }

    private Sampler createDefaultSampler(int sampleRate, int samplingProbability) {
        Sampler sampler;
        if (sampleRate > 0) {
            sampler = RateLimitingSampler.create(sampleRate);
            LOG.info("Default sampler is RateLimitingSampler with sampleRate={}", sampleRate);
        } else {
            sampler = CountingSampler.create((float) samplingProbability / 100);
            LOG.info("Default sampler is CountingSampler with samplingProbability={}", samplingProbability);
        }
        return sampler;
    }

    private Sampler getTracingSampler() {
        return sampleOnlyHttp ? Sampler.NEVER_SAMPLE : defaultSampler();
    }

    private Sampler defaultSampler() {
        return createDefaultSampler(sampleRate, samplingProbability);
    }

    private Reporter<Span> createReporter(boolean remoteReporterEnabled) {
        List<Reporter<Span>> reporters = new ArrayList<>();
        reporters.add(e -> {
            LOG.info("captured {}", e);
            CAPTURED_SPANS.add(e);
        });
        if (remoteReporterEnabled) {
            reporters.add(createRemoteReporter());
        }
        return MulticastReporter.createFrom(reporters);
    }

    private AsyncReporter<Span> createRemoteReporter() {
        OkHttpSender.Builder senderBuilder = OkHttpSender.newBuilder()
            .endpoint(HttpUrl.get(baseUrl).newBuilder().port(port).addPathSegments("api/v2/spans").build())
            .compressionEnabled(compressionEnabled);
        senderBuilder.clientBuilder()
            .addNetworkInterceptor(requestLogger());
        if (maxMessageSize > 0) {
            senderBuilder.messageMaxBytes(maxMessageSize);
        }
        if (maxRequests > 0) {
            senderBuilder.maxRequests(maxRequests);
        }
        AsyncReporter.Builder reporterBuilder = AsyncReporter.builder(senderBuilder.build())
            .queuedMaxSpans(queuedMaxSpans);
        if (queuedMaxBytes > 0) {
            reporterBuilder.queuedMaxBytes(queuedMaxBytes);
        }
        if (closeTimeout > 0) {
            reporterBuilder.closeTimeout(closeTimeout, TimeUnit.MILLISECONDS);
        }
        if (messageTimeout > 0) {
            reporterBuilder.messageTimeout(messageTimeout, TimeUnit.MILLISECONDS);
        }
        return reporterBuilder.build();
    }

    private HttpLoggingInterceptor requestLogger() {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(s -> LoggerFactory.getLogger(OkHttpSender.class).trace(s));
        if (LoggerFactory.getLogger(OkHttpSender.class).isTraceEnabled()) {
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        }
        return interceptor;
    }

    @RequiredArgsConstructor
    private static final class MulticastReporter implements Reporter<Span> {
        private static final Reporter<Span> NOOP = span -> { };
        private final List<Reporter<Span>> reporters;

        static Reporter<Span> createFrom(List<Reporter<Span>> reporters) {
            return reporters.isEmpty() ? NOOP : new MulticastReporter(reporters);
        }

        @Override
        public void report(Span span) {
            reporters.forEach(r -> r.report(span));
        }
    }
}
