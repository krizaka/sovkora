package com.krizaka.sovkora.jobs.api.api.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobCommandTest {

  private static JobCommand commandWith(Map<String, Object> payload) {
    return new JobCommand("job", "user", "feature", payload);
  }

  @Test
  void fullConstructor_setsAllFields() {
    JobCommand command =
        new JobCommand("job-1", "user-1", "a.capability", "a-model", Map.of("key", "value"));

    assertEquals("job-1", command.jobId());
    assertEquals("user-1", command.userId());
    assertEquals("a.capability", command.featureKey());
    assertEquals("a-model", command.model());
    assertEquals(Map.of("key", "value"), command.payload());
  }

  @Test
  void backwardsCompatibleConstructor_setsDefaultModel() {
    assertEquals("default", new JobCommand("job-1", "user-1", "a.capability", Map.of()).model());
  }

  @Test
  void rejects_aCommandWithNoIdentity() {
    Map<String, Object> empty = Map.of();
    assertThrows(
        NullPointerException.class, () -> new JobCommand(null, "user-1", "feat", "model", empty));
    assertThrows(
        NullPointerException.class, () -> new JobCommand("job-1", "user-1", null, "model", empty));
    assertThrows(
        NullPointerException.class, () -> new JobCommand("job-1", "user-1", "feat", null, empty));
  }

  @Test
  void nullPayload_defaultsToEmptyMap() {
    JobCommand command = new JobCommand("job-1", "user-1", "feat", "model", null);

    assertNotNull(command.payload());
    assertTrue(command.payload().isEmpty());
  }

  @Test
  void payload_isDefensivelyCopied() {
    Map<String, Object> original = new HashMap<>();
    original.put("key", "value");
    Map<String, Object> payload = new JobCommand("j", "u", "f", "m", original).payload();

    assertThrows(UnsupportedOperationException.class, () -> payload.put("new", "val"));
  }

  // ── Billing (ADR-033 §6.3) ───────────────────────────────────────────────

  @Test
  void withReservation_stampsTheHoldIntoThePayload_soInFlightMessagesStayReadable() {
    JobCommand stamped = commandWith(Map.of("prompt", "p")).withReservation("hold-7", "corr-1");

    assertEquals("hold-7", stamped.holdId());
    assertEquals("corr-1", stamped.correlationId());
    assertEquals("p", stamped.prompt());
  }

  @Test
  void anUnmeteredCommandHasNoHold_whichIsAValidStateAndNotAnError() {
    JobCommand command = commandWith(Map.of());

    assertNull(command.holdId());
    assertNull(command.correlationId());
  }

  @Test
  void blankHold_readsAsAbsent_soASettlementNeverTargetsAnEmptyId() {
    assertNull(commandWith(Map.of("holdId", "  ")).holdId());
  }

  @Test
  void resolvedModel_prefersThePayloadOverrideThenTheResolvedModel() {
    assertEquals("override", commandWith(Map.of("model", "override")).resolvedModel());
    assertEquals("resolved", new JobCommand("j", "u", "f", "resolved", Map.of()).resolvedModel());
  }

  @Test
  void resolvedModel_isNullWhenNothingWasDecided_whichAsksThePricebookForItsDefault() {
    assertNull(commandWith(Map.of()).resolvedModel());
  }

  // ── Typed payload accessors ──────────────────────────────────────────────

  @Test
  void prompt_readsPromptKeyThenTextKey() {
    assertEquals("hello", commandWith(Map.of("prompt", "hello")).prompt());
    assertEquals("world", commandWith(Map.of("text", "world")).prompt());
    assertNull(commandWith(Map.of()).prompt());
  }

  @Test
  void requirePrompt_absent_throws() {
    JobCommand command = commandWith(Map.of());
    assertThrows(IllegalArgumentException.class, command::requirePrompt);
  }

  @Test
  void typedAccessors_readTheirKeys() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("model", "sd-xl");
    payload.put("voice", "ryan");
    payload.put("durationSeconds", 8);
    payload.put("imagePath", "/in/img.png");
    payload.put("filePath", "/in/file.mp4");
    JobCommand command = commandWith(payload);

    assertEquals("sd-xl", command.requestedModel());
    assertEquals("ryan", command.voice());
    assertEquals(8, command.durationSeconds());
    assertEquals("/in/img.png", command.imagePath());
    assertEquals("/in/file.mp4", command.filePath());
  }

  @Test
  void typedAccessors_absent_returnNull() {
    JobCommand command = commandWith(Map.of());

    assertNull(command.requestedModel());
    assertNull(command.voice());
    assertNull(command.durationSeconds());
    assertNull(command.imagePath());
    assertNull(command.filePath());
  }

  @Test
  void theDataClassSurvivesEveryStamp() {
    // ADR-065: a SENSITIVE step that took a reservation must still be SENSITIVE when it arrives.
    JobCommand command =
        new JobCommand(
            "job-1", "user-1", "orazaka.core.chat.text", "default", Map.of(), DataClass.SENSITIVE);

    assertEquals(DataClass.SENSITIVE, command.withReservation("hold-1", "corr-1").dataClass());
    assertEquals(DataClass.SENSITIVE, command.withDeferredMetering().dataClass());
  }

  @Test
  void aCommandBuiltWithoutAClassDeclaresNone() {
    // Absent, not STANDARD: the job plane refuses it rather than guessing (ADR-065).
    assertNull(new JobCommand("job-2", "user-1", "orazaka.core.chat.text", Map.of()).dataClass());
  }
}
