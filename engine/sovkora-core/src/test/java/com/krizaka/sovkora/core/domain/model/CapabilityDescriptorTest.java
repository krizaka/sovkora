package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a capability is, to the engine: an identity and a switch (ADR-069 §5). */
class CapabilityDescriptorTest {

  /**
   * Every case this class held was about the endpoint rule — a broker-routed capability declaring
   * no path, an HTTP one declaring both halves, half an endpoint refused, blank refused. All four
   * went with {@code uriPath} and {@code httpMethod} (ADR-069 §5): a capability has no HTTP
   * surface, because invoking one is starting a run. What is left to assert is the identity.
   */
  @Test
  @DisplayName("a capability is an identity and a switch; a blank identity is refused")
  void theIdentityIsRequired() {
    CapabilityDescriptor descriptor = new CapabilityDescriptor("orazaka.core.media.image", true);
    assertEquals("orazaka.core.media.image", descriptor.featureKey());
    assertTrue(descriptor.enabled());

    assertThrows(NullPointerException.class, () -> new CapabilityDescriptor(null, true));
    assertThrows(IllegalArgumentException.class, () -> new CapabilityDescriptor("  ", true));
  }
}
