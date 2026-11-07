package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.automationservice.domain.exception.AutomationNotFoundException;
import com.krizaka.sovkora.automationservice.domain.exception.RunNotFoundException;
import com.krizaka.sovkora.automationservice.domain.exception.TooManyAutomationsException;
import com.krizaka.sovkora.automationservice.domain.exception.TooManyTriggersException;
import com.krizaka.sovkora.automationservice.domain.exception.TriggerNotFoundException;
import com.krizaka.sovkora.automationservice.domain.exception.WebhookRejectedException;
import com.krizaka.sovkora.automationservice.domain.model.Rejection;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class RestErrorResolverTest {

  private final RestErrorResolver resolver = new RestErrorResolver();

  @Test
  @DisplayName("a missing automation is a 404 that does not say whose it is")
  void notFound() {
    ProblemDetail problem = resolver.notFound(new AutomationNotFoundException(UUID.randomUUID()));
    assertThat(problem.getStatus()).isEqualTo(404);
    assertThat(problem.getProperties()).containsEntry("code", "automation_not_found");
  }

  @Test
  @DisplayName("the quota is a 409 with its own code")
  void quota() {
    ProblemDetail problem = resolver.tooMany(new TooManyAutomationsException(3));
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getProperties()).containsEntry("code", "too_many_automations");
  }

  @Test
  @DisplayName("a rejected value is a 400 carrying the reason")
  void invalid() {
    ProblemDetail problem = resolver.invalid(new IllegalArgumentException("bad"));
    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getDetail()).isEqualTo("bad");
    assertThat(problem.getProperties()).containsEntry("code", "invalid_request");
  }

  @Test
  @DisplayName("a missing trigger is a 404, and too many triggers a 409 with their own codes")
  void triggers() {
    ProblemDetail missing =
        resolver.triggerNotFound(new TriggerNotFoundException(UUID.randomUUID()));
    assertThat(missing.getStatus()).isEqualTo(404);
    assertThat(missing.getProperties()).containsEntry("code", "trigger_not_found");
    ProblemDetail many = resolver.tooManyTriggers(new TooManyTriggersException(5));
    assertThat(many.getStatus()).isEqualTo(409);
    assertThat(many.getProperties()).containsEntry("code", "too_many_triggers");
  }

  @Test
  @DisplayName(
      "each refusal of a webhook call has its status, its code and, when it is a matter of time, Retry-After")
  void webhookRefusals() {
    UUID first = UUID.randomUUID();
    var tooLarge =
        resolver.webhookRejected(
            new WebhookRejectedException(Rejection.PAYLOAD_TOO_LARGE, 0, null));
    assertThat(tooLarge.getStatusCode().value()).isEqualTo(413);
    var unauthenticated =
        resolver.webhookRejected(new WebhookRejectedException(Rejection.UNAUTHENTICATED, 0, null));
    assertThat(unauthenticated.getStatusCode().value()).isEqualTo(401);
    assertThat(unauthenticated.getBody().getProperties())
        .containsEntry("code", "invalid_webhook_request");
    assertThat(unauthenticated.getHeaders().getFirst("Retry-After")).isNull();
    var replay =
        resolver.webhookRejected(new WebhookRejectedException(Rejection.REPLAYED, 0, first));
    assertThat(replay.getStatusCode().value()).isEqualTo(409);
    assertThat(replay.getBody().getProperties()).containsEntry("runId", first.toString());
    var limited =
        resolver.webhookRejected(new WebhookRejectedException(Rejection.RATE_LIMITED, 7, null));
    assertThat(limited.getStatusCode().value()).isEqualTo(429);
    assertThat(limited.getHeaders().getFirst("Retry-After")).isEqualTo("7");
    var budget =
        resolver.webhookRejected(new WebhookRejectedException(Rejection.DAILY_BUDGET, 3600, null));
    assertThat(budget.getStatusCode().value()).isEqualTo(429);
    assertThat(budget.getBody().getProperties()).containsEntry("code", "daily_budget_reached");
  }

  @Test
  @DisplayName("the unauthenticated answer is the same words whatever the cause")
  void unauthenticatedIsUniform() {
    var one =
        resolver.webhookRejected(new WebhookRejectedException(Rejection.UNAUTHENTICATED, 0, null));
    var two =
        resolver.webhookRejected(new WebhookRejectedException(Rejection.UNAUTHENTICATED, 0, null));
    assertThat(one.getBody().getDetail()).isEqualTo(two.getBody().getDetail());
  }

  @Test
  @DisplayName("a missing run is a 404")
  void runNotFound() {
    ProblemDetail problem = resolver.runNotFound(new RunNotFoundException(UUID.randomUUID()));
    assertThat(problem.getStatus()).isEqualTo(404);
    assertThat(problem.getProperties()).containsEntry("code", "run_not_found");
  }
}
