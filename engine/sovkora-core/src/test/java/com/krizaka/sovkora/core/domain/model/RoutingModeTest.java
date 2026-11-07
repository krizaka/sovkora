package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RoutingModeTest {

  @Test
  void values_containsAllModes() {
    assertEquals(3, RoutingMode.values().length);
  }

  @Test
  void deterministic_hasDescription() {
    assertNotNull(RoutingMode.DETERMINISTIC.description());
    assertFalse(RoutingMode.DETERMINISTIC.description().isBlank());
  }

  @Test
  void agentic_hasDescription() {
    assertNotNull(RoutingMode.AGENTIC.description());
  }

  @Test
  void semantic_hasDescription() {
    assertNotNull(RoutingMode.SEMANTIC.description());
  }

  @Test
  void valueOf_roundTrips() {
    assertEquals(RoutingMode.DETERMINISTIC, RoutingMode.valueOf("DETERMINISTIC"));
    assertEquals(RoutingMode.AGENTIC, RoutingMode.valueOf("AGENTIC"));
    assertEquals(RoutingMode.SEMANTIC, RoutingMode.valueOf("SEMANTIC"));
  }
}
