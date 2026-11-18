package com.krizaka.sovkora.conversation.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.users.domain.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class SignedInUserTest {

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void theUserIsThePrincipalWithItsRoleAndTenant() {
    SignedInUser.signIn("11111111-1111-4111-8111-111111111111", "acme", "ROLE_SERVICE");
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getPrincipal()).isInstanceOf(User.class);
    assertThat(authentication.getAuthorities())
        .extracting(Object::toString)
        .containsExactlyInAnyOrder("ROLE_SERVICE", "TENANT_acme");
  }
}
