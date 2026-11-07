package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest;

import com.krizaka.sovkora.automationservice.application.service.WebhookIngressService;
import com.krizaka.sovkora.automationservice.domain.model.IngressResult;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed inbound webhook: {@code POST /hooks/{triggerId}} — the one request this service
 * answers without a session or a key (ADR-035 amendment, ADR-084). Authentication is the signature
 * ({@code webhook-id}, {@code webhook-timestamp}, {@code webhook-signature}), verified by the
 * ingress service before any work or any write. This class only reads the headers and the raw body
 * (bounded) and renders the answer: {@code 202} with the run, or a problem from {@code
 * RestErrorResolver}.
 */
@RestController
@RequestMapping("/hooks")
class WebhookIngressController {

  private final WebhookIngressService ingress;

  WebhookIngressController(WebhookIngressService ingress) {
    this.ingress = Objects.requireNonNull(ingress, "WebhookIngressService cannot be null");
  }

  /**
   * Receives a signed call and starts the automation it stands for.
   *
   * @param triggerId the trigger, as it appears in the path
   * @param webhookId the {@code webhook-id} header
   * @param timestamp the {@code webhook-timestamp} header
   * @param signature the {@code webhook-signature} header
   * @param request the request, for its length and its body
   * @return {@code 202} with the run id
   */
  @PostMapping("/{triggerId}")
  ResponseEntity<Map<String, Object>> receive(
      @PathVariable String triggerId,
      @RequestHeader(value = "webhook-id", required = false) String webhookId,
      @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
      @RequestHeader(value = "webhook-signature", required = false) String signature,
      HttpServletRequest request) {
    IngressResult result =
        ingress.accept(
            triggerId,
            webhookId,
            timestamp,
            signature,
            request.getContentLengthLong(),
            maxBytes -> readAtMost(request.getInputStream(), maxBytes));
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            Map.of(
                "runId", result.runId().toString(),
                "status", result.status().name().toLowerCase(java.util.Locale.ROOT)));
  }

  /** Reads at most {@code maxBytes + 1} bytes: a body that does not fit is never fully read. */
  private static byte[] readAtMost(InputStream in, int maxBytes) throws IOException {
    byte[] read = in.readNBytes(maxBytes + 1);
    return read.length > maxBytes ? null : read;
  }
}
