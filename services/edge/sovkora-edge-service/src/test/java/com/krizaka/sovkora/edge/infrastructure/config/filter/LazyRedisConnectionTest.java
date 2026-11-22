package com.krizaka.sovkora.edge.infrastructure.config.filter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class LazyRedisConnectionTest {

  @SuppressWarnings("unchecked")
  private static StatefulRedisConnection<String, String> open() {
    return (StatefulRedisConnection<String, String>)
        Proxy.newProxyInstance(
            LazyRedisConnectionTest.class.getClassLoader(),
            new Class<?>[] {StatefulRedisConnection.class},
            (proxy, method, args) -> null);
  }

  @Test
  void anOpenConnectionIsGivenBackAsItIs() {
    StatefulRedisConnection<String, String> connection = open();

    assertSame(connection, new LazyRedisConnection(connection).get());
  }

  @Test
  void aMissingConnectionOrClientIsRefusedAtOnce() {
    assertThrows(
        NullPointerException.class,
        () -> new LazyRedisConnection((StatefulRedisConnection<String, String>) null));
    assertThrows(NullPointerException.class, () -> new LazyRedisConnection((RedisClient) null));
    assertNotNull(open());
  }

  @Test
  void aClientConnectionIsOpenedOnceAcrossThreads() throws Exception {
    java.util.concurrent.atomic.AtomicInteger opened =
        new java.util.concurrent.atomic.AtomicInteger();
    StatefulRedisConnection<String, String> connection = open();
    RedisClient client =
        org.mockito.Mockito.mock(
            RedisClient.class,
            invocation -> {
              opened.incrementAndGet();
              return connection;
            });
    LazyRedisConnection lazy = new LazyRedisConnection(client);
    java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newFixedThreadPool(8);
    try {
      java.util.List<java.util.concurrent.Future<StatefulRedisConnection<String, String>>> gets =
          new java.util.ArrayList<>();
      for (int i = 0; i < 16; i++) {
        gets.add(pool.submit(lazy::get));
      }
      for (var get : gets) {
        assertSame(connection, get.get());
      }
    } finally {
      pool.shutdownNow();
    }
    org.junit.jupiter.api.Assertions.assertEquals(1, opened.get());
  }
}
