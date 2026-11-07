package com.krizaka.sovkora.core.domain.ports.outbound;

/** Port: keeps the result of a tool call for a while, keyed by the tool and its input. */
public interface ToolResultCache {

  /**
   * Reads a result.
   *
   * @param toolId the tool
   * @param cacheKey the tool input the result was computed for
   * @return the cached result, or {@code null} when absent or expired
   */
  String get(String toolId, String cacheKey);

  /**
   * Keeps a result.
   *
   * @param toolId the tool
   * @param cacheKey the tool input
   * @param value the result
   * @param ttlSeconds how long it may be served
   */
  void put(String toolId, String cacheKey, String value, long ttlSeconds);
}
