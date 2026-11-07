package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CapabilityEntityTest {

  @Test
  void setAndGetAllFields() {
    var entity = new CapabilityEntity();
    entity.setFeatureKey("orazaka.core.media.video");
    entity.setHandlerKey("video.generate");
    entity.setIsEnabled(true);

    assertEquals("orazaka.core.media.video", entity.getFeatureKey());
    assertEquals("video.generate", entity.getHandlerKey());
    assertTrue(entity.getIsEnabled());
  }

  @Test
  void defaultValues_areNull() {
    var entity = new CapabilityEntity();
    assertNull(entity.getFeatureKey());
    assertNull(entity.getHandlerKey());
    assertNull(entity.getIsEnabled());
  }
}
