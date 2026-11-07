package com.krizaka.sovkora.knowledge.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActingPrincipalTest {

  @Test
  @DisplayName("a well-formed claim is the principal, with its grants")
  void parsesAClaim() {
    var principal =
        ActingPrincipal.fromClaim(
                Map.of(
                    "sub",
                    "u1",
                    "tenant",
                    "acme",
                    "roles",
                    List.of("ROLE_HR"),
                    "groups",
                    List.of("GROUP_FINANCE")))
            .orElseThrow();

    assertThat(principal.grantees())
        .containsExactlyInAnyOrder("role:ROLE_HR", "group:GROUP_FINANCE", "user:u1");
  }

  @Test
  @DisplayName("a missing, partial or hostile claim is no principal")
  void refusesAnythingElse() {
    assertThat(ActingPrincipal.fromClaim(null)).isEmpty();
    assertThat(ActingPrincipal.fromClaim("acme")).isEmpty();
    assertThat(ActingPrincipal.fromClaim(Map.of("sub", "u1"))).isEmpty();
    assertThat(ActingPrincipal.fromClaim(Map.of("sub", "u1", "tenant", "*"))).isEmpty();
    assertThat(ActingPrincipal.fromClaim(Map.of("sub", "", "tenant", "acme"))).isEmpty();
    assertThat(ActingPrincipal.fromClaim(Map.of("sub", "u1", "tenant", "a,b", "roles", List.of())))
        .isEmpty();
    assertThat(
            ActingPrincipal.fromClaim(
                Map.of("sub", "u1", "tenant", "acme", "roles", List.of("ROLE_X,role:ROLE_HR"))))
        .isEmpty();
    assertThat(ActingPrincipal.fromClaim(Map.of("sub", "u1", "tenant", "acme", "roles", "ROLE_HR")))
        .isEmpty();
  }

  @Test
  @DisplayName("the platform tenant is a source's, never a caller's")
  void platformTenantIsNotACaller() {
    assertThatThrownBy(() -> new ActingPrincipal("*", "u1", Set.of(), Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("an ACL entry is role:, group: or user: and nothing that could break a list")
  void aclEntries() {
    assertThat(Acl.isEntry("role:ROLE_HR")).isTrue();
    assertThat(Acl.isEntry("group:GROUP_FINANCE")).isTrue();
    assertThat(Acl.isEntry("user:3f2a")).isTrue();
    assertThat(Acl.isEntry("everyone")).isFalse();
    assertThat(Acl.isEntry("role:A,role:B")).isFalse();
    assertThat(Acl.isEntry(null)).isFalse();
  }
}
