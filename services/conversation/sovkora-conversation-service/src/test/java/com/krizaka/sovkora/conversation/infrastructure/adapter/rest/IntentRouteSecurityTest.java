package com.krizaka.sovkora.conversation.infrastructure.adapter.rest;

import static com.krizaka.sovkora.conversation.infrastructure.config.SecurityChainFixture.TOKEN;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.sovkora.conversation.infrastructure.config.SecurityChainFixture;
import com.krizaka.sovkora.engine.business.api.UseCaseDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * The {@code intent} resource behind the service's real filter chain: {@code /api/v1/intent/route}
 * is not served, to a caller with no token nor to a signed-in person (krizaka-restricted#350).
 */
@SpringJUnitWebConfig(IntentRouteSecurityTest.Context.class)
class IntentRouteSecurityTest {

  private static final String ENVELOPE =
      """
      {"intentId":"i-1","userId":"u-1","payload":"ping","metadata":{}}
      """;

  @Autowired WebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = SecurityChainFixture.securedMvc(context);
  }

  @Test
  void anUnauthenticatedCallIsRefused() throws Exception {
    mvc.perform(
            post("/api/v1/intent/route").contentType(MediaType.APPLICATION_JSON).content(ENVELOPE))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aSignedInPersonFindsNoRoute() throws Exception {
    mvc.perform(
            post("/api/v1/intent/route")
                .header("Authorization", "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ENVELOPE))
        .andExpect(status().isNotFound());
  }

  @Configuration
  @Import({SecurityChainFixture.class, IntentController.class})
  static class Context {

    @Bean
    UseCaseDispatcher useCaseDispatcher() {
      return mock(UseCaseDispatcher.class);
    }
  }
}
