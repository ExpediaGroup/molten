# Molten metrics support

Provides helper classes for instrumenting reactive code execution with metrics.
 
[com.hotels.reactor.metrics.ReactorInstrument](src/main/java/com/hotels/molten/metrics/ReactorInstrument.java) provides a transformer so the complete reactive execution time from `subscribe` to `onComplete` or `onError` can be measured, not just the time when the reactive flow was created. 

##Properties
The following properties are used to create a `ReactorInstrument`.

###MeterRegistry
We create each timer using `MeterRegistry`, which measure the time taken for short tasks and the count of these tasks.

###Qualifier
With the qualifier, we can set a string with we can identify the current measurement. In the `onComplete` method `qualifier`+`.success` will be generated. In the `onError` method, `qualifier`+`.error` or `qualifier`+`.failed` which depends on the `businessExceptionDecisionMaker`. 

###BusinessExceptionDecisionMaker
By default, this has a value `e -> false` which means every exception will be reported as a failure, but it can be overwritten.

## ReactorInstrument usage
Create a new `ReactorInstrument` object and use it like in the examples:

###Mono
```java
Mono.just(1)
    .map(this::slowIOCall)
    .transform(instrument::mono)
    .subscribe();
```
 
###Flux
```java
Flux.just(1)
    .map(this::slowIOCall)
    .transform(instrument::flux)
    .subscribe();
```
