package com.krizaka.sovkora.automationservice.domain.model;

/** Enumeration representing the lifecycle states of an automation job. */
public enum AutomationJobStatus {
  PENDING_APPROVAL,
  APPROVED,
  RUNNING,
  COMPLETED,
  FAILED,
  AWAITING_CLI_EXECUTION
}
