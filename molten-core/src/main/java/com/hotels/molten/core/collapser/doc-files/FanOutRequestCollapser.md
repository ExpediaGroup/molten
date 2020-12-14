# Fan-out request collapser
Executes individual calls in batches. 
For detailed information and configuration options please see `com.hotels.molten.core.collapser.FanOutRequestCollapser`.

## Fan-out request collapser with batch size 2

```mermaid
    sequenceDiagram
      participant r1 as Request1
      participant r2 as Request2
      participant forc as FanOutRequestCollapser
      participant bs as BulkService
      r1-xforc: get id=1
      r2-xforc: get id=2
      Note right of forc: batch full
      forc-xbs: get ids=1,2
      r1-xforc: get id=3
      bs-xforc: values for ids=2,1
      forc-xr2: value for id=2
      forc-xr1: value for id=1
      Note right of forc: batch wait time expired
      forc-xbs: get ids=3
      bs-xforc: value for id=3
      forc-xr1: value for id=3
```

![](fan_out_request_collapser.png)
