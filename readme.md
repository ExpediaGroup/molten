![](src/site/molten.png)

[![Build Status](https://github.com/ExpediaGroup/molten/workflows/Build/badge.svg)](https://github.com/ExpediaGroup/molten/actions?query=workflow:"Build")
[![Release](https://img.shields.io/github/release/expediagroup/molten.svg)](https://img.shields.io/github/release/expediagroup/molten.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Molten
======

Molten is an opinionated library providing reactive tooling to simplify building production-ready integration solutions using [Reactor](https://projectreactor.io). 
It builds on other libraries to make reactive caching, instrumentation, tracing, health checks, and HTTP client creation simple. 
It also provides implementations for several reactive integration patterns (e.g. request collapsing).
Molten requires Java 11+ to be used.

Some libraries building on:
- Reactive streams
  - [Reactor](https://projectreactor.io)
- Resiliency
  - [Resilience4j](https://resilience4j.readme.io/)
- Caching
  - [Caffeine](https://github.com/ben-manes/caffeine)
  - [Kryo](https://github.com/EsotericSoftware/kryo)
  - [Lettuce](https://lettuce.io/)
- HTTP client
  - [OkHttp](https://square.github.io/okhttp/)
  - [Retrofit](https://square.github.io/retrofit/)
  - [Jackson](https://github.com/FasterXML/jackson)
  - [Netty](https://netty.io/)
  - [Reactor Netty](https://github.com/reactor/reactor-netty)
  - [Protobuf](https://developers.google.com/protocol-buffers)
- Metrics  
  - [Micrometer](https://micrometer.io/)
- Tracing 
  - [Zipkin](https://zipkin.io/)
- Misc
  - [Lombok](https://projectlombok.org/)
  - [Guava](https://github.com/google/guava)
  - [Slf4j](http://www.slf4j.org/)
  - [Logback](http://logback.qos.ch/)

## Requirements

To build Molten you should have the followings available:
- JDK 11+
- Maven 3.6.1+ (or use provided `mvnw`)
- Docker 1.6.0+ (see [TestContainers system requirements](https://www.testcontainers.org/supported_docker_environment/))

## Build

To compile the library and run all tests execute the following: 

```bash
./mvnw clean verify
``` 

To also package the library execute:

```bash
./mvnw clean install
```

# Usage

For your convenience there's a BOM (Bill of Materials) to import consistent module and dependency versions (defines reactor and resilience4j as well).

```xml
<dependency>
    <groupId>com.expediagroup.molten</groupId>
    <artifactId>molten-bom</artifactId>
    <version>${molten.version}</version>
    <scope>import</scope>
    <type>pom</type>
</dependency>
```

To define all dependency versions consistently, one can use the dependencies BOM:
```xml
<dependency>
    <groupId>com.expediagroup.molten</groupId>
    <artifactId>molten-dependencies</artifactId>
    <version>${molten.version}</version>
    <scope>import</scope>
    <type>pom</type>
</dependency>
```     

# Modules
* [molten-core](molten-core/readme.md) - core reactive solutions (e.g. request collapsers)
* [molten-cache](molten-cache/readme.md) - reactive cache support (e.g. reloading cache)
* [molten-health](molten-health/readme.md) - reactive health-check
* [molten-http-client](molten-http-client/readme.md) - reactive http client builder
* [molten-metrics](molten-metrics/readme.md) - reactive metrics support
* [molten-remote-cache](molten-remote-cache/readme.md) - reactive off-heap cache support (e.g. redis)
* [molten-spring-boot](molten-spring-boot/readme.md) - Spring Boot 2 integration support
* [molten-trace](molten-trace/readme.md) - reactive tracing support

# Test support modules
* [molten-test](molten-test/readme.md) - reactive test support
* [molten-test-mockito-autoconfigure](molten-test-mockito-autoconfigure/readme.md) - auto-configured Mockito for reactive test support
* [molten-trace-test](molten-trace-test/readme.md) - reactive tracing test support

# Experimental features
Please note that types and methods annotated with `@com.hotels.molten.core.common.Experimental` are considered unstable and might change without further notice.

# Contributing
Please refer to our [CONTRIBUTING](CONTRIBUTING.md) file.

# License
This project is available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

Copyright 2020 Expedia, Inc.

To add/update licence headers use:
```
mvn license:format
```
