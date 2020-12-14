#molten-test

Provides helper classes for testing reactive code.

## Reactive mocks

To create Mockito mocks over reactive APIs one should use the `@ReactiveMock` annotation.
This creates a mock with default empty answers for reactive return types and also supports default methods on the interfaces.

```java
public class SomeTestNGTest {
    @ReactiveMock
    private ReactiveApi reactiveApi;
    
    @BeforeMethod
    public void initContext() {
        ReactiveMockitoAnnotations.initMocks(this);
    }
    
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

## Unstable types
To test hot publishers with resubscription one can use `com.hotels.molten.test.UnstableMono`.
For example this is useful when retrying behavior needs to be tested against a reactive API which can yield different result for each subscription. 
