# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Added `FanOutRequestCollapser#collapseCallsEagerlyOver` to allow bulk providers to emmit partial results eagerly before the whole bulk call is completed.
### Changed
- Constructor of `FanOutRequestCollapser.Builder` become private, please use the static factory methods to start building one.
- Changed `onEachOperator` based MDC and trace propagation from `reactor.core.publisher.Hooks.onEachOperator` to `reactor.core.publisher.Hooks.addQueueWrapper` based implementation.
- MDC and trace propagation always uses queue wrappers.
### Deprecated
- Deprecated `com.hotels.molten.core.mdc.MoltenMDC.initialize(boolean)` in favour of `com.hotels.molten.core.mdc.MoltenMDC.initialize()`.
- Deprecated `com.hotels.molten.trace.MoltenTrace.initialize(boolean)` in favour of `com.hotels.molten.trace.MoltenTrace.initialize()`.

## [1.2.3]
### Changed
- Dependency version updates.

## [1.2.2]
### Added
- Added `ResilientReactiveCacheBuilder#withPutTimeout` to set a different timeout only for put operations.

## [1.2.1]
### Fixed
- Fixed `FanOutRequestCollapser` not being thread safe when accepting requests from different threads.

## [1.2.0]
### Added
- Added `FanOutRequestCollapser.Builder#withGroupId`, so two instance can be differentiated when observing the logs.
- Added `FanOutRequestCollapser.Builder#withBatchMaxConcurrencyWaitTime` to set the maximum time to wait for executing
  a prepared batch call if there are already max concurrency batches running.
### Changed
- Made `FanOutRequestCollapser#maxConcurrency` limit forced by `Bulkhead` instead of the concurrency of `flatMap`,
  which killed the whole collapser instead of that single call over the limit.
- Dropped support of reactor-core below 3.4.0, by using the new `Sinks` api.
- Made [Vert.x](https://vertx.io/) internal dependency only. It means that vert.x version is no longer managed by `molten-dependencies`.
### Fixed
- Fixed `ReactiveCache` implementations to log the `Throwable#toString()` instead of the message, which can be null.

## [1.1.3]
### Changed
- Migrated most of the unit tests to JUnit 5.
- Extended testing of `ReactiveCache` implementations.

## [1.1.2]
### Added
- Added `ReactiveCache#cachingWith` to use as a caching operator in a reactive chain.
- Added `RetrofitServiceClientBuilder#useProtocols` to set the exact http protocols your client should use.

## [1.1.1]
### Added
- Added the legacy mockito support to the auto-configuration.
  The legacy `@ReactiveMock` mock creation now can be turned off or extended as well.

## [1.1.0]
### Added
- Added spring-boot 2 auto-configuration support with `molten-spring-boot` module.
- Revamped mockito support with auto-configuration. `@Mock` now supports reactor types.

### Deprecated
- `@ReactiveMock` and related classes are now deprecated.

## [1.0.0]
- Initial release
