# molten-health

Reactive health-check implementation.

Implement a `com.hotels.molten.healthcheck.HealthIndicator` for the corresponding component and have it registered with `com.hotels.molten.healthcheck.HealthIndicatorWatcher.watch`.

Use `com.hotels.molten.healthcheck.HealthIndicator.composite` to create a `com.hotels.molten.healthcheck.CompositeHealthIndicator` which can act like both as a health provider and a watcher. Usually a single top level bean of this type should be created and have it injected in the corresponding components.





