package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest;

import com.krizaka.sovkora.automationservice.domain.exception.AutomationNotFoundException;
import com.krizaka.sovkora.automationservice.domain.exception.RunNotFoundException;
import com.krizaka.sovkora.automationservice.domain.exception.TooManyAutomationsException;
import com.krizaka.sovkora.automationservice.domain.exception.TooManyTriggersException;
import com.krizaka.sovkora.automationservice.domain.exception.TriggerNotFoundException;
import com.krizaka.sovkora.automationservice.domain.exception.WebhookRejectedException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * One error shape for the API: RFC 9457 problem details with a stable {@code code} (ADR-083 §2).
 * Messages never echo a secret or a request body.
 */
@RestControllerAdvice
class RestErrorResolver {

  private static final String CODE = "code";

  @ExceptionHandler(AutomationNotFoundException.class)
  ProblemDetail notFound(AutomationNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "automation_not_found", "No such automation.");
  }

  @ExceptionHandler(TooManyAutomationsException.class)
  ProblemDetail tooMany(TooManyAutomationsException e) {
    return problem(HttpStatus.CONFLICT, "too_many_automations", e.getMessage());
  }

  @ExceptionHandler(TriggerNotFoundException.class)
  ProblemDetail triggerNotFound(TriggerNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "trigger_not_found", "No such trigger.");
  }

  @ExceptionHandler(TooManyTriggersException.class)
  ProblemDetail tooManyTriggers(TooManyTriggersException e) {
    return problem(HttpStatus.CONFLICT, "too_many_triggers", e.getMessage());
  }

  /**
   * An inbound webhook call that was refused. Every unauthenticated cause is the same answer, word
   * for word: nothing here says whether the trigger exists.
   */
  @ExceptionHandler(WebhookRejectedException.class)
  ResponseEntity<ProblemDetail> webhookRejected(WebhookRejectedException e) {
    HttpStatus status =
        switch (e.rejection()) {
          case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
          case RATE_LIMITED, DAILY_BUDGET -> HttpStatus.TOO_MANY_REQUESTS;
          case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
          case REPLAYED -> HttpStatus.CONFLICT;
        };
    ProblemDetail problem =
        problem(status, e.rejection().code(), "The webhook call was not accepted.");
    if (e.existingRunId() != null) {
      problem.setProperty("runId", e.existingRunId().toString());
    }
    ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
    if (e.retryAfterSeconds() > 0) {
      response.header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()));
    }
    return response.body(problem);
  }

  @ExceptionHandler(RunNotFoundException.class)
  ProblemDetail runNotFound(RunNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "run_not_found", "No such run.");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail invalid(IllegalArgumentException e) {
    return problem(HttpStatus.BAD_REQUEST, "invalid_request", e.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail unreadable(HttpMessageNotReadableException e) {
    return problem(HttpStatus.BAD_REQUEST, "invalid_request", "The body is not valid JSON.");
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("https://sovkora.com/problems/" + code));
    problem.setProperty(CODE, code);
    return problem;
  }
}
