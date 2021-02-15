# molten-trace

Tracing support built over [Brave](https://github.com/openzipkin/brave).

## Reactor tracing initialization
Unless you are already using something to propagate trace context on reactive flows, the following should be invoked after `brave.Tracing` is initialized.

```
com.hotels.molten.trace.MoltenTrace.initialize()
```

This ensures that components of Molten will not break your traces. 

On the other hand, it has no effect on certain third party scenarios (e.g. callbacks from event loops). For example when using spring webflux with netty you will still need e.g. `spring-cloud-sleuth`.
In this case `MoltenTrace.initialize` can be omitted.  
