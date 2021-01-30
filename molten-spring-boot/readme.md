# Molten spring-boot support

Simplifies integration with Spring Boot 2.

By adding this module to your project Spring will automatically register a listener to initialize core Molten functions with dimensional metrics support. 
See [MoltenCoreSpringBootInitializer](src/main/java/com/hotels/molten/spring/boot/MoltenCoreSpringBootInitializer.java).

## Tracing

Currently, the suggested way of using molten tracing with spring-boot is to make use of [spring-cloud-sleuth](https://spring.io/projects/spring-cloud-sleuth) with the following settings:
```
spring:
  sleuth:
    enabled: true
    reactor:
      enabled: true
      instrumentation-type: decorate_on_each
```
Calling `MoltenTrace.initialize(..)` is not necessary to function properly.

