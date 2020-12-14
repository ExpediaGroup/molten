# Request collapser integration pattern
The following pattern can be used to make service invocations globally cached, deduplicated, and bulk.  

## Async reloading cache over request collapser over fan-out request collapser with batch size 2

```mermaid
    sequenceDiagram
      participant r1 as Request1
      participant r2 as Request2
      participant cache as AsyncReloadingCache
      participant rc as RequestCollapser
      participant forc as FanOutRequestCollapser
      participant bs as BulkService
      r1-xcache: get id=1
      cache->>cache: cache miss
      cache-xrc: get id=1
      rc-xforc: get id=1
      
      r2-xcache: get id=1
      cache->>cache: cache miss
      cache-xrc: get id=1
      
      r1-xcache: get id=2
      cache->>cache: cache hit (with expired TTR)
      cache-xrc: get id=2
      Note right of cache: async reload
      cache-xr1: value for id=2
      rc-xforc: get id=2
      Note right of forc: batch full
      forc-xbs: get ids=1,2
      
      bs-xforc: values for ids=2,1
      forc-xrc: value for id=2
      rc-xcache: value for id=2
      cache-xcache: stores new value for id=2
      
      forc-xrc: value for id=1
      rc-xcache: value for id=1
      cache-xcache: stores value for id=1
      rc-xr1: value for id=1
      rc-xr2: value for id=1
```

![](request_collapser_integration.png)

For async reloading cache implementation please see `ReactiveReloadingCache` in `molten-cache` module.
