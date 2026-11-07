package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StepRoutingRequestTest {

  @Test
  void defaultsToSovereignLocalOnly() {
    assertEquals(
        RoutingPolicy.SOVEREIGN_LOCAL_ONLY, new StepRoutingRequest("step", "chat").policy());
    assertEquals(
        RoutingPolicy.SOVEREIGN_LOCAL_ONLY, new StepRoutingRequest("step", "chat", null).policy());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n"})
  void rejectsInvalidStepId(String value) {
    assertThrows(IllegalArgumentException.class, () -> new StepRoutingRequest(value, "chat"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n"})
  void rejectsInvalidCapability(String value) {
    assertThrows(IllegalArgumentException.class, () -> new StepRoutingRequest("step", value));
  }

  @Test
  void preservesIdentifiersAndExplicitPolicy() {
    StepRoutingRequest request =
        new StepRoutingRequest("step-42", "embedding", RoutingPolicy.LATENCY_FIRST);

    assertEquals("step-42", request.stepId());
    assertEquals("embedding", request.capability());
    assertEquals(RoutingPolicy.LATENCY_FIRST, request.policy());
  }
}
