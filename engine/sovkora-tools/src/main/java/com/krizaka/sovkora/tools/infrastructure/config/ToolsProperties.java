package com.krizaka.sovkora.tools.infrastructure.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration registry for Sovkora Tools, including caching and RAG ingestion properties.
 *
 * @param configs Map of configuration settings per tool.
 */
@ConfigurationProperties(prefix = "orazaka.tools")
public record ToolsProperties(Map<String, ToolConfig> configs) {
  /**
   * Configuration settings for a specific tool.
   *
   * @param cache The caching configuration block.
   * @param rag The RAG chunking and database ingestion configuration block.
   */
  public record ToolConfig(CacheConfig cache, RagConfig rag) {}

  /**
   * Configuration settings for caching tool execution results.
   *
   * @param enabled Whether caching is active.
   * @param ttlSeconds Time-To-Live in seconds for cached values.
   */
  public record CacheConfig(boolean enabled, long ttlSeconds) {}

  /**
   * Configuration settings for RAG database ingestion for a specific tool.
   *
   * @param enabled Whether RAG ingestion is active for the tool.
   * @param chunkerType The type/strategy of the text chunker to use.
   * @param sourceTable The name of the database source table.
   */
  public record RagConfig(boolean enabled, String chunkerType, String sourceTable) {}
}
