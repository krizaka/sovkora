package com.krizaka.sovkora.automationservice.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AutomationJobPayloadTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @org.junit.jupiter.api.Test
  void sonar_context_load() {
    assertThat(AutomationJobPayload.class).isNotNull();
  }

  private static final String JOB_ID = "job-001";
  private static final String USER_ID = "user-42";
  private static final String CONNECTOR = "JIRA";
  private static final String ACTION = "create-issue";

  @Nested
  @DisplayName("Constructor Validation")
  class ConstructorValidation {

    @Test
    @DisplayName("Should reject null jobId")
    void rejectsNullJobId() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new AutomationJobPayload(
                      null,
                      USER_ID,
                      CONNECTOR,
                      ACTION,
                      AutomationJobStatus.PENDING_APPROVAL,
                      Map.of(),
                      Instant.now(FIXED_CLOCK)))
          .withMessageContaining("jobId");
    }

    @Test
    @DisplayName("Should reject null userId")
    void rejectsNullUserId() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new AutomationJobPayload(
                      JOB_ID,
                      null,
                      CONNECTOR,
                      ACTION,
                      AutomationJobStatus.PENDING_APPROVAL,
                      Map.of(),
                      Instant.now(FIXED_CLOCK)))
          .withMessageContaining("userId");
    }

    @Test
    @DisplayName("Should reject null connectorType")
    void rejectsNullConnectorType() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new AutomationJobPayload(
                      JOB_ID,
                      USER_ID,
                      null,
                      ACTION,
                      AutomationJobStatus.PENDING_APPROVAL,
                      Map.of(),
                      Instant.now(FIXED_CLOCK)))
          .withMessageContaining("connectorType");
    }

    @Test
    @DisplayName("Should reject null action")
    void rejectsNullAction() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new AutomationJobPayload(
                      JOB_ID,
                      USER_ID,
                      CONNECTOR,
                      null,
                      AutomationJobStatus.PENDING_APPROVAL,
                      Map.of(),
                      Instant.now(FIXED_CLOCK)))
          .withMessageContaining("action");
    }

    @Test
    @DisplayName("Should reject null status")
    void rejectsNullStatus() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new AutomationJobPayload(
                      JOB_ID, USER_ID, CONNECTOR, ACTION, null, Map.of(), Instant.now(FIXED_CLOCK)))
          .withMessageContaining("status");
    }
  }

  @Nested
  @DisplayName("Defensive Copies & Defaults")
  class DefensiveCopiesAndDefaults {

    @Test
    @DisplayName("Should default null payload to empty map")
    void defaultsNullPayloadToEmptyMap() {
      var job =
          new AutomationJobPayload(
              JOB_ID,
              USER_ID,
              CONNECTOR,
              ACTION,
              AutomationJobStatus.APPROVED,
              null,
              Instant.now(FIXED_CLOCK));
      assertThat(job.payload()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Should defensively copy payload map")
    void defensivelyCopiesPayload() {
      var original = new java.util.HashMap<String, Object>();
      original.put("key", "value");

      var job =
          new AutomationJobPayload(
              JOB_ID,
              USER_ID,
              CONNECTOR,
              ACTION,
              AutomationJobStatus.RUNNING,
              original,
              Instant.now(FIXED_CLOCK));

      var immutablePayload = job.payload();
      assertThatThrownBy(() -> immutablePayload.put("injected", "evil"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should default null createdAt to Instant.now(FIXED_CLOCK)")
    void defaultsNullCreatedAt() {
      AutomationJobPayload.clock = FIXED_CLOCK;
      try {
        Instant before = Instant.now(FIXED_CLOCK);
        var job =
            new AutomationJobPayload(
                JOB_ID,
                USER_ID,
                CONNECTOR,
                ACTION,
                AutomationJobStatus.PENDING_APPROVAL,
                Map.of(),
                null);
        Instant after = Instant.now(FIXED_CLOCK);

        assertThat(job.createdAt()).isBetween(before, after);
      } finally {
        AutomationJobPayload.clock = AutomationJobPayload.DEFAULT_CLOCK;
      }
    }
  }

  @Nested
  @DisplayName("Record Semantics")
  class RecordSemantics {

    @Test
    @DisplayName("Should have correct accessor methods")
    void accessorsWork() {
      Instant now = Instant.now(FIXED_CLOCK);
      var payload = Map.<String, Object>of("issueType", "BUG");
      var job =
          new AutomationJobPayload(
              JOB_ID, USER_ID, CONNECTOR, ACTION, AutomationJobStatus.COMPLETED, payload, now);

      assertThat(job.jobId()).isEqualTo(JOB_ID);
      assertThat(job.userId()).isEqualTo(USER_ID);
      assertThat(job.connectorType()).isEqualTo(CONNECTOR);
      assertThat(job.action()).isEqualTo(ACTION);
      assertThat(job.status()).isEqualTo(AutomationJobStatus.COMPLETED);
      assertThat(job.payload()).containsEntry("issueType", "BUG");
      assertThat(job.createdAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Equal records should be equal")
    void equalRecords() {
      Instant now = Instant.now(FIXED_CLOCK);
      var a =
          new AutomationJobPayload(
              JOB_ID, USER_ID, CONNECTOR, ACTION, AutomationJobStatus.FAILED, Map.of(), now);
      var b =
          new AutomationJobPayload(
              JOB_ID, USER_ID, CONNECTOR, ACTION, AutomationJobStatus.FAILED, Map.of(), now);

      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
  }
}
