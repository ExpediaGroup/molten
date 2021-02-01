#molten-test-mockito-autoconfigure

Provides auto-configured Mockito for testing reactive code.
Included by [molten-test](../molten-test/readme.md) as a transitive dependency.

## How to disable

If you need to disable this feature because it causes any error or
there is already a `org.mockito.configuration.MockitoConfiguration` on your test classpath,
just exclude it from the `molten-test` dependency.

```xml
  <dependency>
    <groupId>com.expediagroup.molten</groupId>
    <artifactId>molten-test</artifactId>
    <version>${molten.version}</version>
    <scope>test</scope>
    <exclusions>
      <exclusion>
        <groupId>com.expediagroup.molten</groupId>
        <artifactId>molten-test-mockito-autoconfigure</artifactId>
      </exclusion>
    </exclusions>
  </dependency>
```

## How to merge with your custom MockitoConfiguration

If you would keep to use reactive mocks along with your custom configuration,
just [disable the feature](#how-to-disable) and set up the default answer in your `org.mockito.configuration.MockitoConfiguration`.

```java
public class MockitoConfiguration extends DefaultMockitoConfiguration {
    @Override
    public Answer<Object> getDefaultAnswer() {
        return new ReactiveAnswer(super.getDefaultAnswer());
    }
}
```
