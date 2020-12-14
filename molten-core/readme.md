# molten-core

Provides the core functionalities for Molten. 

## Reactor initialization
In order to have proper logging and context propagation the following should be invoked before any other Reactor calls in your application.

```
com.hotels.molten.core.MoltenCore.initialize();
```

It sets Slf4j for logging and enables internal Reactor metrics if enabled via `MoltenCore.setMetricsEnabled(boolean)`.

## Context propagation
Context propagation in reactive flows is not always trivial. 
To be able to pass around implicit contexts (which are usually handled via `ThreadLocal`) extra steps should be taken.

There are mainly two scenarios where context propagation should happen:
- tasks submitted to async schedulers
- returning to reactive flows via callbacks from non-reactor managed threads
 
### Schedulers
To propagate contexts for scheduled tasks, one should register a `onSchedule` hook via `reactor.core.scheduler.Schedulers.onScheduleHook`. See `com.hotels.molten.core.mdc.MoltenMDC.MDCCopyingAction` for an example hook. 

### Callbacks
Publishers with custom threading solutions often calls back to reactive flows from a thread which is not managed by Reactor. 
These kind of scenarios are also found in non-blocking libraries (e.g. netty).

While Reactor provides a way to transform flows at each operator using `reactor.core.publisher.Hooks.onEachOperator(String, Function)` it is usually unnecessary everywhere and can also be expensive.
Molten provides a generic way to register context propagators for flows with such callbacks, only invoked where needed.

Calling `com.hotels.molten.core.MoltenCore.registerContextPropagator(String, Function)` with a transformer function will add it to registered context propagators. 
Wherever explicit context propagation is needed, `com.hotels.molten.core.MoltenCore.propagateContext()` should be used as a transformer. e.g. `aMono.transform(MoltenCore.propagateContext())` 
This will apply all registered context propagators. 

To create a custom context propagator you can use `com.hotels.molten.core.common.AbstractNonFusingSubscription` as a base class. See `com.hotels.molten.core.mdc.MoltenMDC.propagate()` for an example. 

## MDC
Using `org.slf4j.MDC` to propagate diagnostic information (logging, request correlation, tracing, etc.) is a common solution. 
Support for MDC is provided via `com.hotels.molten.core.mdc.MoltenMDC`.
Call `com.hotels.molten.core.mdc.MoltenMDC.initialize()` to set up required hooks and context propagators for MDC. 
This should be done as soon as possible, before any Reactor calls. 

## Metrics
Molten supports both dimensional and hierarchical metrics.  
This can be controlled via [com.hotels.molten.core.metrics.MoltenMetrics](src/main/java/com/hotels/molten/core/metrics/MoltenMetrics.java).
To enable dimensional metrics one should invoke `MoltenMetrics.setDimensionalMetricsEnabled(true)` or set system property `MOLTEN_METRICS_DIMENSIONAL_METRICS_ENABLED=true`.
When dimensional metric names are enabled, the hierarchial name can also be added as a label with `MoltenMetrics.setGraphiteIdMetricsLabelEnabled(true)` or setting system property `MOLTEN_METRICS_GRAPHITE_ID_LABEL_ADDED=true`.
 
## Request collapsing

There are generic solutions to make more effective service invocations with request collapsing:

* [request collapser](src/main/java/com/hotels/molten/core/collapser/doc-files/RequestCollapser.md)
* [fan-out request collapser](src/main/java/com/hotels/molten/core/collapser/doc-files/FanOutRequestCollapser.md)

A complete integration pattern can be found [here](src/main/java/com/hotels/molten/core/collapser/doc-files/RequestCollapserIntegration.md) 

 
