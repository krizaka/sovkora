package com.krizaka.sovkora.interceptor.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.sovkora.core.domain.ports.outbound.KnowledgeService;
import com.krizaka.sovkora.core.domain.ports.outbound.McpOrchestrator;
import com.krizaka.sovkora.core.domain.ports.outbound.MemoryResolver;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnrichmentAutoConfigurationTest {

  @Test
  @DisplayName("EnrichmentAutoConfiguration registers all required enrichment beans")
  void registersBeans() {
    EnrichmentAutoConfiguration config = new EnrichmentAutoConfiguration();
    CoreProperties props = mock(CoreProperties.class);
    KnowledgeService knowledge = mock(KnowledgeService.class);
    McpOrchestrator mcp = mock(McpOrchestrator.class);
    MemoryResolver memory = mock(MemoryResolver.class);

    RagInterceptor rag = config.ragInterceptor(props, knowledge);
    McpInterceptor mcpInt = config.mcpInterceptor(mcp);
    MemoryInterceptor mem = config.memoryInterceptor(memory);
    DynamicMemoryCondenser condenser = config.dynamicMemoryCondenser();
    HybridRagResolver hybrid = config.hybridRagResolver();

    assertThat(rag).isNotNull();
    assertThat(mcpInt).isNotNull();
    assertThat(mem).isNotNull();
    assertThat(condenser).isNotNull();
    assertThat(hybrid).isNotNull();
  }
}
