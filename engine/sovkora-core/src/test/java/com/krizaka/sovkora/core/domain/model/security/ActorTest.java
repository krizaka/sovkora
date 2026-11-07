package com.krizaka.sovkora.core.domain.model.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.Authority;
import com.krizaka.sovkora.core.domain.model.Context;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActorTest {

  @Test
  @DisplayName("roles, groups and tenant are told apart by prefix")
  void classifiesAuthorities() {
    Actor actor =
        Actor.of("u1", List.of("ROLE_USER", "ROLE_HR", "GROUP_FINANCE", "TENANT_acme"))
            .orElseThrow();

    assertThat(actor.tenant()).isEqualTo("acme");
    assertThat(actor.roles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_HR");
    assertThat(actor.groups()).containsExactly("GROUP_FINANCE");
  }

  @Test
  @DisplayName("without a tenant authority a user is a tenant of one")
  void defaultTenantIsTheUser() {
    assertThat(Actor.of("u1", List.of("ROLE_USER")).orElseThrow().tenant()).isEqualTo("u1");
  }

  @Test
  @DisplayName("anonymous, malformed or ambiguous identities are no actor")
  void closedByDefault() {
    assertThat(Actor.from(Context.anonymous())).isEmpty();
    assertThat(Actor.from((Context) null)).isEmpty();
    assertThat(Actor.of("", List.of())).isEmpty();
    assertThat(Actor.of("u,1", List.of("ROLE_USER"))).isEmpty();
    assertThat(Actor.of("u1", List.of("TENANT_a", "TENANT_b"))).isEmpty();
    // a tenant authority that is not a plain name is ignored, never trusted
    assertThat(Actor.of("u1", List.of("TENANT_*")).orElseThrow().tenant()).isEqualTo("u1");
  }

  @Test
  @DisplayName("an authority that could split a list is ignored, not trusted")
  void hostileAuthoritiesAreDropped() {
    Actor actor = Actor.of("u1", List.of("ROLE_USER", "ROLE_X\",\"ROLE_ADMIN")).orElseThrow();

    assertThat(actor.roles()).containsExactly("ROLE_USER");
  }

  @Test
  @DisplayName("an actor comes from the platform keys of the turn, not from a preference")
  void fromUserMetadata() {
    assertThat(
            Actor.from(Map.<String, Object>of("userId", "u1", "roles", List.of("ROLE_HR")))
                .orElseThrow()
                .holdsAny(Set.of("ROLE_HR")))
        .isTrue();
    assertThat(Actor.from(Map.<String, Object>of("userId", "u1"))).isEmpty();
    assertThat(Actor.from(Map.<String, Object>of("preference.roles", List.of("ROLE_HR"))))
        .isEmpty();
  }

  @Test
  @DisplayName("an empty requirement is held by nobody, a role or group by its holders")
  void holdsAny() {
    Actor actor = Actor.of("u1", List.of("ROLE_USER", "GROUP_FINANCE")).orElseThrow();

    assertThat(actor.holdsAny(Set.of())).isFalse();
    assertThat(actor.holdsAny(Set.of("ROLE_HR"))).isFalse();
    assertThat(actor.holdsAny(Set.of("ROLE_HR", "ROLE_USER"))).isTrue();
    assertThat(actor.holdsAny(Set.of("GROUP_FINANCE"))).isTrue();
  }

  @Test
  @DisplayName("the cache scope changes with the user, the tenant and every role")
  void scopeSeparatesActors() {
    Actor user = Actor.of("u1", List.of("ROLE_USER")).orElseThrow();

    assertThat(user.scope()).isEqualTo(Actor.of("u1", List.of("ROLE_USER")).orElseThrow().scope());
    assertThat(user.scope())
        .isNotEqualTo(Actor.of("u2", List.of("ROLE_USER")).orElseThrow().scope());
    assertThat(user.scope())
        .isNotEqualTo(Actor.of("u1", List.of("ROLE_USER", "ROLE_HR")).orElseThrow().scope());
    assertThat(user.scope())
        .isNotEqualTo(Actor.of("u1", List.of("ROLE_USER", "TENANT_acme")).orElseThrow().scope());
  }

  @Test
  @DisplayName("an actor built from a Context carries its authorities")
  void fromContext() {
    Context context = new Context("u1", "c1", Map.of(), Set.of(new Authority("ROLE_HR")));

    assertThat(Actor.from(context).orElseThrow().roles()).containsExactly("ROLE_HR");
  }
}
