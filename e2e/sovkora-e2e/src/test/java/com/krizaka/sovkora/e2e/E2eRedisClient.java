package com.krizaka.sovkora.e2e;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import java.util.List;

/**
 * Live Lettuce Redis infrastructure accessor for E2E assertions.
 *
 * <p>Connects directly to the provisioned Redis container via system properties injected by
 * Failsafe. No mocking, no Spring context — raw Lettuce against the live Redis instance.
 */
final class E2eRedisClient {

  private static final String REDIS_URL = System.getProperty("redis.url", "redis://localhost:6379");

  private E2eRedisClient() {}

  /**
   * Sends a PING command to Redis and returns the response.
   *
   * @return "PONG" if Redis is reachable.
   */
  static String ping() {
    try (RedisClient client = RedisClient.create(RedisURI.create(REDIS_URL));
        StatefulRedisConnection<String, String> conn = client.connect()) {
      return conn.sync().ping();
    }
  }

  /**
   * Returns all keys matching the given glob pattern.
   *
   * @param pattern Redis key glob (e.g., "bucket4j:*").
   * @return List of matching keys.
   */
  static List<String> keys(String pattern) {
    try (RedisClient client = RedisClient.create(RedisURI.create(REDIS_URL));
        StatefulRedisConnection<String, String> conn = client.connect()) {
      return conn.sync().keys(pattern);
    }
  }

  /**
   * Returns the total number of keys in the current Redis database.
   *
   * @return Key count.
   */
  static long dbSize() {
    try (RedisClient client = RedisClient.create(RedisURI.create(REDIS_URL));
        StatefulRedisConnection<String, String> conn = client.connect()) {
      return conn.sync().dbsize();
    }
  }

  /**
   * Checks if Redis is connectable by performing a PING handshake.
   *
   * @return true if PING returns PONG.
   */
  static boolean isConnectable() {
    try {
      return "PONG".equals(ping());
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Flushes the current Redis database to reset all rate-limit buckets.
   *
   * <p>Called before security contract tests to prevent cross-test boundary contamination of
   * Bucket4j token state.
   *
   * @return "OK" if flush succeeded.
   */
  static String flushDb() {
    try (RedisClient client = RedisClient.create(RedisURI.create(REDIS_URL));
        StatefulRedisConnection<String, String> conn = client.connect()) {
      return conn.sync().flushdb();
    }
  }
}
