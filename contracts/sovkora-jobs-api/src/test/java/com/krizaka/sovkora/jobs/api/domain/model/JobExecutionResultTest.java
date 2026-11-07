package com.krizaka.sovkora.jobs.api.api.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobExecutionResultTest {

  @Test
  void carriesTheOutputAndWhatTheExecutionMeasured() {
    JobExecutionResult result =
        new JobExecutionResult(Map.of("content", "hello"), Map.of("tokens", 128), "llava:7b");

    assertEquals("hello", result.output().get("content"));
    assertEquals(128, result.consumption().get("tokens"));
    assertEquals("llava:7b", result.model());
  }

  @Test
  @DisplayName("An unmeasured execution reports empty — released, not billed at its estimate")
  void unmeasuredExecutionReportsEmptyConsumption() {
    assertTrue(JobExecutionResult.of(Map.of("content", "hello")).consumption().isEmpty());
    assertTrue(new JobExecutionResult(Map.of(), null, null).consumption().isEmpty());
  }

  @Test
  void isDefensivelyCopied() {
    Map<String, Object> output = new HashMap<>();
    output.put("content", "hello");
    JobExecutionResult result = JobExecutionResult.of(output);

    output.put("content", "tampered");

    assertEquals("hello", result.output().get("content"));
    assertThrows(UnsupportedOperationException.class, () -> result.output().put("x", "y"));
  }

  @Test
  void rejects_aResultWithNoOutput() {
    assertThrows(NullPointerException.class, () -> new JobExecutionResult(null, Map.of(), null));
  }

  @Test
  @DisplayName("[ADR-041] the engine that ran travels with what it measured")
  void carriesTheResolvedModel() {
    var result =
        JobExecutionResult.measured(
            Map.of("analysis", "a room"), Map.of("tokens", 1800), "llava:7b");

    assertEquals("llava:7b", result.model());
    assertEquals(1800, result.consumption().get("tokens"));
  }

  @Test
  @DisplayName("A blank model is null, so the capability's default rate applies")
  void blankModelBecomesNull() {
    assertNull(new JobExecutionResult(Map.of(), Map.of(), "  ").model());
  }
}
