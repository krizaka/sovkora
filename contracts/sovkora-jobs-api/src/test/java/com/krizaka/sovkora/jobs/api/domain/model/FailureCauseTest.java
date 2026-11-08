package com.krizaka.sovkora.jobs.api.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The contract that stops a missing declaration from costing an actor money (ADR-053). */
class FailureCauseTest {

  @Test
  @DisplayName("a declared cause is read as declared, case-insensitively")
  void readsWhatWasDeclared() {
    assertEquals(FailureCause.GUARD_REFUSAL, FailureCause.of("GUARD_REFUSAL"));
    assertEquals(FailureCause.INPUT_INVALID, FailureCause.of("input_invalid"));
    assertEquals(FailureCause.TIMEOUT, FailureCause.of("  TIMEOUT  "));
  }

  @Test
  @DisplayName("silence is an executor fault — never a refusal, never the user's fault")
  void silenceFailsClosed() {
    assertEquals(FailureCause.EXECUTOR_FAULT, FailureCause.of(null));
    assertEquals(FailureCause.EXECUTOR_FAULT, FailureCause.of(""));
    assertEquals(FailureCause.EXECUTOR_FAULT, FailureCause.of("   "));
  }

  @Test
  @DisplayName("a word nobody defined is an executor fault, not the nearest match")
  void unknownFailsClosed() {
    // The failure this guards: a producer inventing "REFUSED" and being read as GUARD_REFUSAL, or
    // "BAD_INPUT" and being read as INPUT_INVALID — which would bill an actor for our vocabulary
    // drift. An unrecognised word means we do not know, and not knowing releases.
    assertEquals(FailureCause.EXECUTOR_FAULT, FailureCause.of("REFUSED"));
    assertEquals(FailureCause.EXECUTOR_FAULT, FailureCause.of("BAD_INPUT"));
    assertEquals(FailureCause.EXECUTOR_FAULT, FailureCause.of("EXECUTION_TIMEOUT_EXCEEDED"));
  }

  @Test
  @DisplayName("we bill for work performed correctly, and never for our own failure")
  void settlementFollowsWhoFailed() {
    assertIterableEquals(
        List.of(FailureCause.GUARD_REFUSAL, FailureCause.INPUT_INVALID),
        Arrays.stream(FailureCause.values()).filter(FailureCause::settlesMeasuredWork).toList());
    assertIterableEquals(
        List.of(
            FailureCause.EXECUTOR_FAULT, FailureCause.PLATFORM_UNAVAILABLE, FailureCause.TIMEOUT),
        Arrays.stream(FailureCause.values())
            .filter(cause -> !cause.settlesMeasuredWork())
            .toList());
  }

  @Test
  @DisplayName("only one cause accuses the actor, and it is not the refusal")
  void onlyInputInvalidBlamesTheActor() {
    // A refusal and an invalid payload settle the same and read very differently in a protected
    // pack's trail: one says we protected someone, the other says they sent us something unusable.
    assertIterableEquals(
        List.of(FailureCause.INPUT_INVALID),
        Arrays.stream(FailureCause.values()).filter(FailureCause::blamesTheInput).toList());
  }

  @Test
  @DisplayName("the vocabulary is closed: five causes, argued in ADR-053 §2")
  void theVocabularyIsClosed() {
    // Not decoration. A sixth cause is a settlement or an accusation nobody argued for, and this
    // test is where that argument has to happen — UPSTREAM_REFUSAL is the one the cloud launch
    // will need, and it is deliberately absent while no producer on this machine can set it.
    assertIterableEquals(
        List.of(
            FailureCause.GUARD_REFUSAL,
            FailureCause.INPUT_INVALID,
            FailureCause.EXECUTOR_FAULT,
            FailureCause.PLATFORM_UNAVAILABLE,
            FailureCause.TIMEOUT),
        List.of(FailureCause.values()));
  }
}
