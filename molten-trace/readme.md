# molten-trace

Tracing support built over [Brave](https://github.com/openzipkin/brave).

## Reactor initialization
Usually trace context is stored in `org.slf4j.MDC`. For such cases `com.hotels.molten.core.mdc.MoltenMDC.initialize()` will ensure that trace contexts are also propagated properly.

Otherwise, the following should be invoked after `brave.Tracing` is initialized. 

```
com.hotels.molten.trace.MoltenTrace.initialize()
```
