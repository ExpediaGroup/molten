# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Added the legacy mockito support to the auto-configuration.
  Behavior of the legacy `@ReactiveMock` can be turned off as well.

## [1.1.0]
### Added
- Added spring-boot 2 auto-configuration support with `molten-spring-boot` module.
- Revamped mockito support with auto-configuration. `@Mock` now supports reactor types.

### Deprecated
- `@ReactiveMock` and related classes are now deprecated.

## [1.0.0]
- Initial release
