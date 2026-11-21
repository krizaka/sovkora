package com.krizaka.sovkora.conversation.infrastructure.adapter.rest;

import static com.krizaka.sovkora.conversation.infrastructure.config.SecurityChainFixture.ADMIN_TOKEN;
import static com.krizaka.sovkora.conversation.infrastructure.config.SecurityChainFixture.TOKEN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.sovkora.conversation.infrastructure.config.SecurityChainFixture;
import com.krizaka.sovkora.engine.core.application.engine.GraphEngine;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.InfrastructureStatusProvider;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.sovkora.engine.core.infrastructure.config.CoreProperties;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * The sovereign readiness report behind the service's real filter chain (krizaka-restricted#348):
 * it names the default provider, the model server's address and the vector store's state, so only
 * an administrator reads it — the operator console's caller.
 */
@SpringJUnitWebConfig(StatusHealthSecurityTest.Context.class)
@TestPropertySource(properties = "spring.ai.ollama.base-url=http://model-server.internal:11434")
class StatusHealthSecurityTest {

  @Autowired WebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = SecurityChainFixture.securedMvc(context);
  }

  @Test
  void anUnauthenticatedCallIsRefused() throws Exception {
    mvc.perform(get("/api/v1/status/health")).andExpect(status().isUnauthorized());
  }

  @Test
  void aSignedInPersonWhoIsNotAnAdministratorIsRefused() throws Exception {
    mvc.perform(get("/api/v1/status/health").header("Authorization", "Bearer " + TOKEN))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAdministratorReadsTheReport() throws Exception {
    mvc.perform(get("/api/v1/status/health").header("Authorization", "Bearer " + ADMIN_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultProvider").value("ollama"));
  }

  @Configuration
  @Import({SecurityChainFixture.class, StatusController.class})
  static class Context {

    @Bean
    static PropertySourcesPlaceholderConfigurer placeholders() {
      return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    InfrastructureStatusProvider infrastructureStatusProvider() {
      return mock(InfrastructureStatusProvider.class);
    }

    @Bean
    DataSource dataSource() {
      return mock(DataSource.class);
    }

    @Bean
    ModelCatalogProvider modelCatalogProvider() {
      return mock(ModelCatalogProvider.class);
    }

    @Bean
    CoreProperties coreProperties() {
      CoreProperties properties = mock(CoreProperties.class);
      when(properties.defaultProvider()).thenReturn("ollama");
      return properties;
    }

    @Bean
    GraphEngine graphEngine() {
      return mock(GraphEngine.class);
    }
  }
}
