package com.krizaka.sovkora.conversation.infrastructure.config;

import static com.krizaka.sovkora.conversation.infrastructure.config.SecurityChainFixture.TOKEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * The service's real filter chain reads a bearer token from the {@code Authorization} header only:
 * the same valid token in the query string authenticates nothing (URLs reach access logs, proxies
 * and browser history, a header does not).
 */
@SpringJUnitWebConfig(BearerTokenLocationTest.Context.class)
class BearerTokenLocationTest {

  @Autowired WebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = SecurityChainFixture.securedMvc(context);
  }

  @Test
  void theHeaderAuthenticates() throws Exception {
    mvc.perform(get("/api/v1/probe").header("Authorization", "Bearer " + TOKEN))
        .andExpect(status().isOk());
  }

  @Test
  void theQueryStringDoesNot() throws Exception {
    mvc.perform(get("/api/v1/probe").queryParam("access_token", TOKEN))
        .andExpect(status().isUnauthorized());
  }

  @Configuration
  @Import(SecurityChainFixture.class)
  static class Context {

    @Bean
    Probe probe() {
      return new Probe();
    }
  }

  @RestController
  static class Probe {

    @GetMapping("/api/v1/probe")
    String ok() {
      return "ok";
    }
  }
}
