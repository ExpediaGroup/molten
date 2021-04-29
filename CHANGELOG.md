# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Changed
- Made [Vert.x](https://vertx.io/) internal dependency only. It means that vert.x version is no longer managed by `molten-dependencies`.
### Fixed
- Fixed `ReactiveCache` implementations to log the `Throwable#toString()` instead of the message, which can be null.

## [1.1.3]
### Changed
- Migrated most of the unit tests to JUnit 5.
- Extended testing of ReactiveCache implementations.

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
