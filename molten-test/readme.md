#molten-test

Provides helper classes for testing reactive code.

## Reactive mocks

After this module is on the test classpath, reactive Mockito mocks are created over your reactive APIs.
Reactive mock is a mock with default empty answers for reactive return types.

```java
@ExtendWith(MockitoExtension.class)
public class SomeJunitJupiterTest {
    @Mock
    private ReactiveApi reactiveApi;
    
    @Test
    public void shouldEmitStubValue() {
        when(reactiveApi.getAll(ID)).thenReturn(Flux.just(VALUE_A, VALUE_B));

        StepVerifier.create(reactiveApi.getAll(ID)).expectNext(VALUE_A, VALUE_B).verifyComplete();
    }

    @Test
    public void shouldEmitEmptyByDefault() {
        StepVerifier.create(reactiveApi.getAll(ID)).verifyComplete();
    }

    @Test
    public void shouldSupportDefaultMethod() {
        when(reactiveApi.getAll(ID)).thenReturn(Flux.just(VALUE_A, VALUE_B));

        StepVerifier.create(reactiveApi.getFirst(ID)).expectNext(VALUE_A).verifyComplete();
    }
    
    private interface ReactiveApi {
        Flux<String> getAll(int id);

        default Mono<String> getFirst(int id) {
            return getAll(id).firstOrError();
        }
    }
}
```

This is achieved by configuring the default answer in `org.mockito.configuration.MockitoConfiguration`.
If you are already configuring Mockito through that class, or just want to turn this feature off,
see the options on [molten-test-mockito-autoconfigure](../molten-test-mockito-autoconfigure/readme.md).

## Unstable types
To test hot publishers with resubscription one can use `com.hotels.molten.test.UnstableMono`.
For example this is useful when retrying behavior needs to be tested against a reactive API which can yield different result for each subscription. 
