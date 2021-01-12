package org.springframework.cloud.sleuth.brave.bridge;

import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.TraceContext;
import reactor.core.Fuseable;
import reactor.core.publisher.Operators;

import com.hotels.molten.trace.TraceContextPropagatingSubscriber;

public class MoltenSleuthAdapter {
    public static <T> Function<Publisher<T>, Publisher<T>> propagate() {
        return (Function<Publisher<T>, Publisher<T>>) Operators.<T, T>liftPublisher((p, sub) -> {
            if (p instanceof Fuseable.ScalarCallable) {
                return sub;
            }
            TraceContext traceContext = sub.currentContext().getOrDefault(TraceContext.class, null);
            if (traceContext == null) {
                return sub;
            }
            CurrentTraceContext currentTraceContext = sub.currentContext().getOrDefault(CurrentTraceContext.class, null);
            if (currentTraceContext == null) {
                return sub;
            }
            return new TraceContextPropagatingSubscriber(sub, BraveTraceContext.toBrave(traceContext), BraveCurrentTraceContext.toBrave(currentTraceContext));
        });
    }
}
