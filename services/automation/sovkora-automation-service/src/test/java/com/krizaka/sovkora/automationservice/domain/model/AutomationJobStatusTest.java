package com.krizaka.sovkora.automationservice.domain.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutomationJobStatusTest {

  @Test
  @DisplayName("Should have exactly 6 lifecycle states")
  void hasExpectedStateCount() {
    assertThat(AutomationJobStatus.values()).hasSize(6);
  }

  @Test
  @DisplayName("Should contain PENDING_APPROVAL initial state")
  void containsPendingApproval() {
    assertThat(AutomationJobStatus.valueOf("PENDING_APPROVAL"))
        .isEqualTo(AutomationJobStatus.PENDING_APPROVAL);
  }

  @Test
  @DisplayName("Should contain APPROVED transition state")
  void containsApproved() {
    assertThat(AutomationJobStatus.valueOf("APPROVED")).isEqualTo(AutomationJobStatus.APPROVED);
  }

  @Test
  @DisplayName("Should contain RUNNING execution state")
  void containsRunning() {
    assertThat(AutomationJobStatus.valueOf("RUNNING")).isEqualTo(AutomationJobStatus.RUNNING);
  }

  @Test
  @DisplayName("Should contain COMPLETED terminal state")
  void containsCompleted() {
    assertThat(AutomationJobStatus.valueOf("COMPLETED")).isEqualTo(AutomationJobStatus.COMPLETED);
  }

  @Test
  @DisplayName("Should contain FAILED terminal state")
  void containsFailed() {
    assertThat(AutomationJobStatus.valueOf("FAILED")).isEqualTo(AutomationJobStatus.FAILED);
  }

  @Test
  @DisplayName("Should contain AWAITING_CLI_EXECUTION state")
  void containsAwaitingCliExecution() {
    assertThat(AutomationJobStatus.valueOf("AWAITING_CLI_EXECUTION"))
        .isEqualTo(AutomationJobStatus.AWAITING_CLI_EXECUTION);
  }

  @Test
  @DisplayName("Should reject unknown status values")
  void rejectsUnknownStatus() {
    assertThatThrownBy(() -> AutomationJobStatus.valueOf("CANCELLED"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("name() and toString() should return SCREAMING_SNAKE_CASE")
  void nameMatchesConvention() {
    for (AutomationJobStatus status : AutomationJobStatus.values()) {
      assertThat(status.name()).matches("[A-Z_]+");
    }
  }
}
