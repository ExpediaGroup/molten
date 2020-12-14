# molten-trace

Tracing support built over [Brave](https://github.com/openzipkin/brave).

## Reactor initialization
In order to have proper tracing support, the following should be invoked before any other Reactor calls in your app.

```
com.hotels.molten.trace.MoltenTrace.initialize
```
