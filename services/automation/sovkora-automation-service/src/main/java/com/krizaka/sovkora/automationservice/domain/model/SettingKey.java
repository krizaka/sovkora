package com.krizaka.sovkora.automationservice.domain.model;

/**
 * The business parameters of the automation service: keys of the active rule set ({@code
 * automation_settings.rules}), each with the default used when the key is absent and the hard
 * bounds code clamps it to (ADR-027, ADR-079 §1). Never a constant of the logic.
 */
public enum SettingKey {
  /** How many automations one owner may hold. */
  MAX_AUTOMATIONS_PER_OWNER("maxAutomationsPerUser", 20, 1, 200),
  /** The largest serialized size of an automation's fixed inputs. */
  MAX_INPUTS_BYTES("maxInputsBytes", 16_384, 256, 65_536),
  /** How many triggers one automation may hold. */
  MAX_TRIGGERS_PER_AUTOMATION("maxTriggersPerAutomation", 5, 1, 20),
  /** How many runs one automation may start in a UTC day. */
  MAX_RUNS_PER_DAY("maxRunsPerDay", 20, 1, 1_000),
  /** The largest body a webhook call may carry. */
  WEBHOOK_BODY_MAX_BYTES("webhookBodyMaxBytes", 65_536, 1_024, 1_048_576),
  /** How far a webhook timestamp may be from now, either way. */
  WEBHOOK_TOLERANCE_SECONDS("webhookToleranceSeconds", 300, 30, 900),
  /** Calls per minute to webhooks, all triggers together, before anything is looked up. */
  WEBHOOK_GLOBAL_PER_MINUTE("webhookGlobalPerMinute", 3_000, 60, 60_000),
  /** Calls per minute to one webhook trigger, before its signature is checked. */
  WEBHOOK_PREVERIFY_PER_MINUTE("webhookPreverifyPerMinute", 600, 10, 6_000),
  /** Verified calls per minute to one webhook trigger. */
  WEBHOOK_RUNS_PER_MINUTE("webhookRunsPerMinute", 60, 1, 600),
  /** How long the previous secret keeps verifying after a rotation. */
  WEBHOOK_SECRET_GRACE_HOURS("webhookSecretGraceHours", 24, 0, 168),
  /** How long a webhook-id is remembered. */
  WEBHOOK_REPLAY_RETENTION_HOURS("webhookReplayRetentionHours", 24, 1, 720),
  /** How late a schedule slot may be fired before the catch-up policy decides. */
  SCHEDULE_LATE_TOLERANCE_SECONDS("scheduleLateToleranceSeconds", 120, 10, 3_600),
  /** How long a run may stay queued before the sweep starts it. */
  RUN_QUEUED_STALE_SECONDS("runQueuedStaleSeconds", 30, 5, 600),
  /** How long a running run may go without an event before its Studio run is read. */
  RUN_RECONCILE_AFTER_SECONDS("runReconcileAfterSeconds", 120, 30, 3_600);

  private final String key;
  private final int defaultValue;
  private final int minimum;
  private final int maximum;

  SettingKey(String key, int defaultValue, int minimum, int maximum) {
    this.key = key;
    this.defaultValue = defaultValue;
    this.minimum = minimum;
    this.maximum = maximum;
  }

  /**
   * The key in the rule set document.
   *
   * @return the key stored in {@code automation_settings.rules}
   */
  public String key() {
    return key;
  }

  /**
   * The value used when the key is absent or unreadable.
   *
   * @return the default
   */
  public int defaultValue() {
    return defaultValue;
  }

  /**
   * Keeps a stored value inside the bounds code accepts.
   *
   * @param value the stored value
   * @return the value, raised to the minimum or lowered to the maximum
   */
  public int clamp(int value) {
    return Math.max(minimum, Math.min(maximum, value));
  }
}
