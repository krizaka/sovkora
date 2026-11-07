package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.automationservice.domain.model.CatchUp;
import com.krizaka.sovkora.automationservice.domain.model.NewTrigger;
import com.krizaka.sovkora.automationservice.domain.model.ScheduleSpec;
import com.krizaka.sovkora.automationservice.domain.model.TriggerKind;
import java.util.List;

/**
 * The body that adds a trigger.
 *
 * @param kind {@code webhook} or {@code schedule}
 * @param time {@code HH:mm}, for a schedule
 * @param zone a region time zone such as {@code America/Toronto}, for a schedule
 * @param days weekdays such as {@code MON}; every day when absent
 * @param catchUp {@code once} or {@code skip}; once when absent
 */
public record TriggerRequest(
    String kind, String time, String zone, List<String> days, String catchUp) {

  /**
   * The validated draft.
   *
   * @return the draft, or an {@link IllegalArgumentException} naming what is wrong
   */
  public NewTrigger toDraft() {
    TriggerKind parsed = TriggerKind.parse(kind);
    ScheduleSpec schedule =
        parsed == TriggerKind.SCHEDULE ? ScheduleSpec.parse(time, zone, days) : null;
    return new NewTrigger(parsed, schedule, CatchUp.parse(catchUp));
  }
}
