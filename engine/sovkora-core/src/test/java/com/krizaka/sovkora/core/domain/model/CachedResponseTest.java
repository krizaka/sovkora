package com.krizaka.sovkora.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** A turn answered before inference: carried by the context, distinct from a refusal. */
class CachedResponseTest {

  private static PromptContext plain() {
    return new PromptContext("hello", Map.of("userId", "u-1"));
  }

  @Test
  void aPlainContextCarriesNoCachedResponse() {
    assertThat(plain().cachedResponse()).isEmpty();
  }

  @Test
  void aCachedResponseTravelsWithItsTierAndSimilarity() {
    var answered = plain().withCachedResponse(new CachedResponse("Hi!", "SEMANTIC", 0.97));
    assertThat(answered.cachedResponse()).contains(new CachedResponse("Hi!", "SEMANTIC", 0.97));
    assertThat(answered.rawUserQuery()).isEqualTo("hello");
  }

  @Test
  void itDoesNotDisturbTheCreditHold() {
    var answered =
        plain().withBillingHold("hold-9").withCachedResponse(new CachedResponse("x", "EXACT", 1.0));
    assertThat(answered.billingHoldId()).isEqualTo("hold-9");
  }

  @Test
  void aBlankAnswerIsNotAnAnswer() {
    assertThrows(IllegalArgumentException.class, () -> new CachedResponse("  ", "EXACT", 1.0));
    assertThrows(IllegalArgumentException.class, () -> new CachedResponse("x", "", 1.0));
    assertThrows(IllegalArgumentException.class, () -> new CachedResponse("x", "EXACT", 1.5));
  }
}
