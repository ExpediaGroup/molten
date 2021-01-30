package com.hotels.molten.spring.web;

import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.filter.OrderedWebFilter;
import org.springframework.cloud.sleuth.autoconfig.instrument.web.SleuthWebProperties;
import org.springframework.cloud.sleuth.brave.bridge.MoltenSleuthBridge;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import com.hotels.molten.core.MoltenCore;
import com.hotels.molten.trace.MoltenTrace;

@Slf4j
public class SleuthMoltenBridgeWebFilter implements OrderedWebFilter {
    @Override
    public int getOrder() {
        return SleuthWebProperties.TRACING_FILTER_ORDER + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
//        return Mono.deferContextual(context -> WebFluxSleuthOperators.withSpanInScope(context, new Callable<Mono<Void>>() {
//
//            @Override
//            public Mono<Void> call() throws Exception {
//                return chain.filter(exchange).subscribeOn(Schedulers.parallel());
//            }
//        }));
        return chain.filter(exchange)
            .transform(MoltenTrace.propagate())
            .contextWrite(ctx -> {
                TraceContext traceContext = MoltenSleuthBridge.extractTraceContextFrom(ctx);
                if (traceContext != null) {
                    LOG.info("Extracting sleuth trace context {}", traceContext);
                    ctx = ctx.put(TraceContext.class, traceContext);
                }
                return ctx;
            });
//        return Mono.subscriberContext()
//            .flatMap(ctx -> WebFluxSleuthOperators.withSpanInScope(ctx, () -> chain.filter(exchange).transform(MoltenTrace.propagate())));
    }
}
