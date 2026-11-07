package com.krizaka.sovkora.jobs.api.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A capability declaration names a dotted orazaka key and a job routing key, or is refused. */
class CapabilityDeclarationTest {

  private static CapabilityDeclaration declare(String featureKey) {
    return new CapabilityDeclaration(
        featureKey, "handler", "job.media.generate", null, null, "BATCH", null, null, true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"orazaka.media.image", "orazaka.core.chat.completion", "orazaka.a1.b2"})
  @DisplayName("a dotted orazaka key of two segments or more is accepted")
  void acceptsDottedKeys(String featureKey) {
    assertEquals(featureKey, declare(featureKey).featureKey());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "orazaka.media",
        "orazaka.media.",
        "orazaka..image",
        "orazaka.Media.image",
        "acme.media.image",
        "orazaka.media.image-hd",
        " orazaka.media.image",
        ""
      })
  @DisplayName("anything else is refused")
  void refusesOtherKeys(String featureKey) {
    IllegalArgumentException refused =
        assertThrows(IllegalArgumentException.class, () -> declare(featureKey));
    assertTrue(refused.getMessage().contains("dotted orazaka key"));
  }

  @Test
  @DisplayName("a null key is refused")
  void refusesNull() {
    assertThrows(IllegalArgumentException.class, () -> declare(null));
  }

  @Test
  @DisplayName("a key of fifty thousand segments is judged, not a stack overflow")
  void longKeyIsJudgedWithoutStackOverflow() {
    String accepted = "orazaka.media" + ".x".repeat(50_000);
    String refused = accepted + ".";

    assertEquals(accepted, declare(accepted).featureKey());
    assertThrows(IllegalArgumentException.class, () -> declare(refused));
  }

  @Test
  @DisplayName("empty schemas read as the undeclared contract")
  void emptySchemasDefault() {
    CapabilityDeclaration declaration = declare("orazaka.media.image");

    assertEquals("{}", declaration.inputSchema());
    assertEquals("{}", declaration.outputSchema());
  }
}
