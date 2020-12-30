# Molten spring-boot support

Simplifies integration with Spring Boot 2.

By adding this module to your project Spring will automatically register a listener to initialize core Molten functions with dimensional metrics support. 
See [MoltenCoreSpringBootInitializer](src/main/java/com/hotels/molten/spring/boot/MoltenCoreSpringBootInitializer.java).

If you also have distributed tracing enabled, don't forget to initialize it as well. See [molten-trace](../molten-trace/readme.md).
