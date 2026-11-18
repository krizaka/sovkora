package com.krizaka.sovkora.conversation.infrastructure.adapter.rest;

import com.krizaka.users.domain.model.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/** The signed-in caller of a product door's tests: one user, of one tenant, with one role. */
final class SignedInUser {

  private SignedInUser() {}

  /**
   * Puts the user in the security context, as the session filter would.
   *
   * @param id the user's id (a UUID)
   * @param tenant the user's tenant
   * @param role the role it holds
   */
  static void signIn(String id, String tenant, String role) {
    Set<String> authorities = Set.of(role, "TENANT_" + tenant);
    User user =
        new User(
            UUID.fromString(id),
            "actor-" + id,
            id + "@sovkora.test",
            true,
            authorities,
            Map.of(),
            List.of());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(
            user, "token", authorities.stream().map(SimpleGrantedAuthority::new).toList()));
    SecurityContextHolder.setContext(context);
  }
}
