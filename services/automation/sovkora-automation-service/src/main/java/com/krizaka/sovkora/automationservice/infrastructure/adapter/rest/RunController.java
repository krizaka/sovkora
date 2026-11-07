package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest;

import com.krizaka.sovkora.automationservice.application.service.RunHistoryService;
import com.krizaka.sovkora.automationservice.infrastructure.adapter.rest.dto.RunResponse;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The run history of the caller's automations, newest first. */
@RestController
@RequestMapping("/api/v1/automations")
class RunController {

  private final RunHistoryService history;

  RunController(RunHistoryService history) {
    this.history = Objects.requireNonNull(history, "RunHistoryService cannot be null");
  }

  /**
   * Every run of every automation of the caller.
   *
   * @param limit how many at most (default 50, up to 200)
   * @param actor the authenticated caller
   * @return the runs, newest first
   */
  @GetMapping("/runs")
  List<RunResponse> all(
      @RequestParam(required = false) Integer limit, @AuthenticationPrincipal Jwt actor) {
    return history.list(actor.getSubject(), null, limit).stream().map(RunResponse::from).toList();
  }

  /**
   * One run, with the webhook body it was started with.
   *
   * @param runId the run
   * @param actor the authenticated caller
   * @return the run; {@code 404} when the caller has none with this id
   */
  @GetMapping("/runs/{runId}")
  RunResponse one(@PathVariable UUID runId, @AuthenticationPrincipal Jwt actor) {
    return RunResponse.from(history.get(actor.getSubject(), runId));
  }

  /**
   * The runs of one automation.
   *
   * @param automationId the automation
   * @param limit how many at most (default 50, up to 200)
   * @param actor the authenticated caller
   * @return the runs, newest first
   */
  @GetMapping("/{automationId}/runs")
  List<RunResponse> ofAutomation(
      @PathVariable UUID automationId,
      @RequestParam(required = false) Integer limit,
      @AuthenticationPrincipal Jwt actor) {
    return history.list(actor.getSubject(), automationId, limit).stream()
        .map(RunResponse::from)
        .toList();
  }
}
