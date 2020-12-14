# molten-trace-test

Provides testing support for traces using molten-trace.

Extend `com.hotels.molten.trace.test.AbstractTracingTest` to have support for testing tracing logic.
Use `com.hotels.molten.trace.test.SpanMatcher` to simplify assertions against spans.

Also, see `com.hotels.molten.trace.test.TracingTestSupport` for bringing up Brave/Zipkin based test infrastructure. 
