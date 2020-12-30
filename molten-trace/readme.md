# molten-trace

Tracing support built over [Brave](https://github.com/openzipkin/brave).

## Reactor initialization
In order to have proper tracing support, the following should be invoked after `brave.Tracing` is initialized. 

```
com.hotels.molten.trace.MoltenTrace.initialize()
```
