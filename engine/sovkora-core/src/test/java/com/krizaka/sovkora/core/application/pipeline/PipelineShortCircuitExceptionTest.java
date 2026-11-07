package com.krizaka.sovkora.core.application.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PipelineShortCircuitExceptionTest {

  @Test
  @DisplayName("carries the reason the transport layer maps to a status")
  void carriesTheRefusalCode() {
    PipelineShortCircuitException refusal =
        new PipelineShortCircuitException(
            "EntitlementInterceptor", "capability_not_in_plan", "Plan free excludes chat", null);

    assertEquals("EntitlementInterceptor", refusal.interceptorId());
    assertEquals("capability_not_in_plan", refusal.reason());
    assertEquals("Plan free excludes chat", refusal.getMessage());
  }

  @Test
  @DisplayName("keeps the underlying refusal, so the paywall renders from the ledger's numbers")
  void keepsTheCause() {
    IllegalStateException cause = new IllegalStateException("no credits");
    PipelineShortCircuitException refusal =
        new PipelineShortCircuitException("EntitlementInterceptor", "x", "y", cause);

    assertSame(cause, refusal.getCause());
  }
}
