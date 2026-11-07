package com.krizaka.sovkora.business.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudioPayloadTest {

  private static final UUID INSTALLATION = UUID.fromString("9f1c0a10-0000-4000-8000-000000000010");

  @Test
  void accepts_anInstallationWithItsInputs() {
    StudioPayload payload = new StudioPayload(INSTALLATION, Map.of("trade", "plombier"));

    assertEquals(INSTALLATION, payload.installationId());
    assertEquals("plombier", payload.inputs().get("trade"));
  }

  @Test
  void rejects_aRunWithNoInstallation() {
    assertThrows(NullPointerException.class, () -> new StudioPayload(null, Map.of()));
  }

  @Test
  void treatsNullInputsAsEmpty_soNoUseCaseNullChecksThem() {
    assertTrue(new StudioPayload(INSTALLATION, null).inputs().isEmpty());
  }

  @Test
  void copiesTheInputs_soTheCallerCannotChangeThemAfterTheIntentionIsBuilt() {
    Map<String, Object> source = new HashMap<>(Map.of("trade", "plombier"));
    StudioPayload payload = new StudioPayload(INSTALLATION, source);

    source.put("trade", "électricien");

    assertEquals("plombier", payload.inputs().get("trade"));
  }

  @Test
  void isAPermittedVariantOfTheSealedPayloadHierarchy() {
    Payload payload = new StudioPayload(INSTALLATION, Map.of());

    assertTrue(payload instanceof StudioPayload);
  }
}
