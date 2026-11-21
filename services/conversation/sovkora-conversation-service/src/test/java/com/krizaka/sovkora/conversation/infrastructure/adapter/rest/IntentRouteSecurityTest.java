package com.krizaka.sovkora.conversation.infrastructure.adapter.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.sovkora.conversation.infrastructure.config.SecurityConfig;
import com.krizaka.sovkora.engine.business.api.UseCaseDispatcher;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.port.UserDirectoryClient;
import jakarta.servlet.Filter;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * The {@code intent} resource behind the service's real filter chain: {@code /api/v1/intent/route}
 * is not served, to a caller with no token nor to a signed-in person (krizaka-restricted#350).
 */
@SpringJUnitWebConfig(IntentRouteSecurityTest.Context.class)
class IntentRouteSecurityTest {

  private static final String TOKEN = "a-valid-session-jwt";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String ENVELOPE =
      """
      {"intentId":"i-1","userId":"u-1","payload":"ping","metadata":{}}
      """;

  @Autowired WebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
            .build();
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
  @EnableWebMvc
  @Import({SecurityConfig.class, IntentController.class})
  static class Context {

    @Bean
    UseCaseDispatcher useCaseDispatcher() {
      return mock(UseCaseDispatcher.class);
    }

    @Bean
    UserDirectoryClient userDirectoryClient() {
      UserDirectoryClient directory = mock(UserDirectoryClient.class);
      when(directory.getUser(USER_ID.toString()))
          .thenReturn(
              new User(
                  USER_ID, "person", "person@example.test", true, Set.of("ROLE_USER"), Map.of()));
      return directory;
    }

    @Bean
    JwtDecoder sessionJwtDecoder() {
      return token -> {
        if (!TOKEN.equals(token)) {
          throw new BadJwtException("not a session token");
        }
        return Jwt.withTokenValue(token)
            .header("alg", "HS256")
            .subject(USER_ID.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
      };
    }

    @Bean
    Filter operationGraphFilter() {
      return (request, response, chain) -> chain.doFilter(request, response);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
      return new UrlBasedCorsConfigurationSource();
    }
  }
}
