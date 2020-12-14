# Request collapser
Deduplicates similar calls happening in a short time-window. 
For detailed information and configuration options please see `com.hotels.molten.core.collapser.RequestCollapser`. 

## Request collapsing with release on TTL expiration

```mermaid
    sequenceDiagram
      participant r1 as Request1
      participant r2 as Request2
      participant rc as RequestCollapser
      participant s as Service
      r1-xrc: get id=1
      rc-xs: get id=1
      s-xrc: value for id=1
      rc-xr1: value for id=1
      r2-xrc: get id=1
      rc-xr2: value for id=1
      Note right of rc: TTL releases request
```

![](request_collapser_ttl.png)

## Request collapsing with release when finished

```mermaid
    sequenceDiagram
      participant r1 as Request1
      participant r2 as Request2
      participant rc as RequestCollapser
      participant s as Service
      r1-xrc: get id=1
      rc-xs: get id=1
      r2-xrc: get id=1
      s-xrc: value for id=1
      rc-xr1: value for id=1
      Note right of rc: Request released
      rc-xr2: value for id=1
```

![](request_collapser_release.png)
