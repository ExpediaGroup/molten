# reactive-remote-cache

A reactive remote cache implementation over Redis.

In order to have a reactive cache using Redis one should create a shared cluster connection with: 

```java
StatefulRedisClusterConnection<Object, Object> redisConnection = RedisConnectionBuilder.builder()
    .withRedisSeedUris(redisSeedUris)
    .withClusterClientOptions(clientOptions) //optional
    .withCodec(someCodec) // should be some generic Object typed codec
    .withMeterRegistry(meterRegistry, "remote-cache.sharedRemoteCache.redis")
    .createConnection()
```

Then use this connection to create a reactive named cache over it.

```java
 ReactiveCache<NamedCacheKey<Integer>, CachedValue<List<String>>> sharedCache = ResilientSharedReactiveRedisCacheBuilder.<Integer, List<String>>builder()
    .withRedisConnection(() -> redisConnection)
    .withSharedCacheName("sharedRemoteCache")
    .withMaxConcurrency(100)
    .withMeterRegistry(meterRegistry)
    .createCache()
```

This instance can be used to create multiple reactive caches with their own region. See [reactive cache builder](../reactive-cache/readme.md) for more information.
