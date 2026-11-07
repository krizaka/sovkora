package com.krizaka.sovkora.jobs.domain.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnroutableCapabilityExceptionTest {

  @Test
  void namesTheCapability_becauseAStackTraceDoesNotSayWhichRowIsMissing() {
    UnroutableCapabilityException failure = new UnroutableCapabilityException("a.capability");

    assertEquals("a.capability", failure.featureKey());
    assertTrue(failure.getMessage().contains("a.capability"));
  }

  @Test
  void carriesTheCallSiteContext_soAnAdminCanFindTheStepWithoutReadingLogs() {
    UnroutableCapabilityException failure =
        new UnroutableCapabilityException("a.capability", "step 'describe'");

    assertTrue(failure.getMessage().contains("step 'describe'"));
    assertEquals("a.capability", failure.featureKey());
  }
}
