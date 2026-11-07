package com.krizaka.sovkora.core.application.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.security.Actor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActorScopeTest {

  private static final Actor HR = Actor.of("hr-1", List.of("ROLE_HR")).orElseThrow();
  private static final Actor USER = Actor.of("u-1", List.of("ROLE_USER")).orElseThrow();

  @Test
  @DisplayName("outside a scope there is no actor")
  void noActorOutsideAScope() {
    assertThat(ActorScope.current()).isEmpty();
  }

  @Test
  @DisplayName("inside a scope the actor is current, and the previous one comes back after")
  void scopesNestAndRestore() {
    ActorScope.callAs(
        HR,
        () -> {
          assertThat(ActorScope.current()).contains(HR);
          ActorScope.callAs(USER, () -> assertThat(ActorScope.current()).contains(USER));
          assertThat(ActorScope.current()).contains(HR);
          return null;
        });
    assertThat(ActorScope.current()).isEmpty();
  }

  @Test
  @DisplayName("a failing body does not leave its actor behind")
  void cleansUpAfterAFailure() {
    try {
      ActorScope.callAs(
          HR,
          () -> {
            throw new IllegalStateException("boom");
          });
    } catch (IllegalStateException expected) {
      // the point is what follows
    }
    assertThat(ActorScope.current()).isEmpty();
  }
}
