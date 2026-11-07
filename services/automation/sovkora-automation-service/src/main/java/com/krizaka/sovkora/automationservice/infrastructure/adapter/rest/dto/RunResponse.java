package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.krizaka.sovkora.automationservice.domain.model.AutomationRun;
import com.krizaka.sovkora.automationservice.domain.model.RunDetail;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * One run of the history. {@code payload} (the webhook body the run was started with) is in the
 * detail of a run only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunResponse(
    UUID id,
    UUID automationId,
    String automationName,
    UUID triggerId,
    String trigger,
    String status,
    UUID studioRunId,
    String errorCode,
    String errorMessage,
    String webhookId,
    Integer payloadBytes,
    Instant startedAt,
    Instant finishedAt,
    Map<String, Object> payload) {

  /**
   * A run for the list.
   *
   * @param run the run
   * @return the response, without the payload
   */
  public static RunResponse from(AutomationRun run) {
    return of(run, null);
  }

  /**
   * A run with its payload.
   *
   * @param detail the run and what started it
   * @return the response
   */
  public static RunResponse from(RunDetail detail) {
    return of(detail.run(), detail.payload());
  }

  private static RunResponse of(AutomationRun run, Map<String, Object> payload) {
    return new RunResponse(
        run.id(),
        run.automationId(),
        run.automationName(),
        run.triggerId(),
        run.triggerKind() == null ? null : run.triggerKind().name().toLowerCase(Locale.ROOT),
        run.status().name().toLowerCase(Locale.ROOT),
        run.studioRunId(),
        run.errorCode(),
        run.errorMessage(),
        run.webhookId(),
        run.payloadBytes(),
        run.startedAt(),
        run.finishedAt(),
        payload);
  }
}
