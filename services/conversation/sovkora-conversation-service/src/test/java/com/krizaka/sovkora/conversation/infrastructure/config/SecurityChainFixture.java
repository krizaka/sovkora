package com.krizaka.sovkora.conversation.infrastructure.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.port.UserDirectoryClient;
import jakarta.servlet.Filter;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * The service's real {@link SecurityConfig} with the beans it needs, for a test that drives the
 * filter chain: one person, signed in with {@link #TOKEN}; every other bearer token is refused.
 */
@Configuration
@EnableWebMvc
@Import(SecurityConfig.class)
public class SecurityChainFixture {

  /** The one session token the decoder accepts. */
  public static final String TOKEN = "a-valid-session-jwt";

  private static final UUID USER_ID = UUID.randomUUID();

  /** A {@link MockMvc} over {@code context} with the service's security filter chain in front. */
  public static MockMvc securedMvc(WebApplicationContext context) {
    return MockMvcBuilders.webAppContextSetup(context)
        .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
        .build();
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
