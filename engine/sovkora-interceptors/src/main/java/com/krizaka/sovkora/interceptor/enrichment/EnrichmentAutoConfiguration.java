package com.krizaka.sovkora.interceptor.enrichment;

import com.krizaka.sovkora.core.domain.ports.outbound.KnowledgeService;
import com.krizaka.sovkora.core.domain.ports.outbound.McpOrchestrator;
import com.krizaka.sovkora.core.domain.ports.outbound.MemoryResolver;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the enrichment interceptor module.
 *
 * <p>Conditionally registers {@link RagInterceptor}, {@link McpInterceptor}, and {@link
 * MemoryInterceptor} beans only when their outbound port dependencies are present.
 *
 * @since 1.0.0
 */
@AutoConfiguration
public class EnrichmentAutoConfiguration {

  @Bean
  @ConditionalOnBean(KnowledgeService.class)
  RagInterceptor ragInterceptor(CoreProperties properties, KnowledgeService knowledgeService) {
    return new RagInterceptor(properties, knowledgeService);
  }

  @Bean
  @ConditionalOnBean(McpOrchestrator.class)
  McpInterceptor mcpInterceptor(McpOrchestrator mcpOrchestrator) {
    return new McpInterceptor(mcpOrchestrator);
  }

  @Bean
  @ConditionalOnBean(MemoryResolver.class)
  MemoryInterceptor memoryInterceptor(MemoryResolver memoryResolver) {
    return new MemoryInterceptor(memoryResolver);
  }

  /**
   * Registers the {@link DynamicMemoryCondenser} bean.
   *
   * <p>Condenses older conversation history turns into a compact session-facts summary to prevent
   * context window dilution while preserving recent turns verbatim.
   *
   * @return The dynamic memory condenser interceptor.
   */
  @Bean
  DynamicMemoryCondenser dynamicMemoryCondenser() {
    return new DynamicMemoryCondenser();
  }

  /**
   * Registers the {@link HybridRagResolver} bean.
   *
   * <p>Coordinates Reciprocal Rank Fusion (RRF) over BM25 sparse and PGVector dense retrieval with
   * tenant-isolation constraints.
   *
   * @return The hybrid RAG resolver interceptor.
   */
  @Bean
  HybridRagResolver hybridRagResolver() {
    return new HybridRagResolver();
  }

  /**
   * Registers the {@link BrandContextInterceptor} bean.
   *
   * <p>Unconditional, unlike its neighbours: it depends on no outbound port, only on a preference
   * namespace that is absent for every request that is not a Studio step — in which case it does
   * nothing. Gating it on a port that does not exist would be inventing a dependency to justify a
   * condition.
   *
   * @return The brand-context enrichment interceptor.
   */
  @Bean
  BrandContextInterceptor brandContextInterceptor() {
    return new BrandContextInterceptor();
  }
}
