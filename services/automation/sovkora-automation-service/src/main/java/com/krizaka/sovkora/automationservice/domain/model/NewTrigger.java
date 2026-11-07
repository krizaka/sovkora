package com.krizaka.sovkora.automationservice.domain.model;

import java.util.Objects;

/**
 * What a caller asks for when adding a trigger to an automation.
 *
 * @param kind webhook or schedule
 * @param schedule the slot, required for a schedule and refused for a webhook
 * @param catchUp the missed-slot policy of a schedule; once when null
 */
public record NewTrigger(TriggerKind kind, ScheduleSpec schedule, CatchUp catchUp) {

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public NewTrigger {
    Objects.requireNonNull(kind, "kind is required");
    if (kind == TriggerKind.SCHEDULE && schedule == null) {
      throw new IllegalArgumentException("a schedule trigger needs time, zone and days");
    }
    if (kind == TriggerKind.WEBHOOK && schedule != null) {
      throw new IllegalArgumentException("a webhook trigger has no schedule");
    }
    catchUp = catchUp == null ? CatchUp.ONCE : catchUp;
  }
}
