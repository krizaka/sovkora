package com.krizaka.sovkora.job.infrastructure.adapter.rest;

import com.krizaka.sovkora.job.application.service.PodTokenService;
import com.krizaka.sovkora.job.domain.model.PodKey;
import com.krizaka.sovkora.job.infrastructure.adapter.rest.dto.PodRevocationResponse;
import com.krizaka.sovkora.job.infrastructure.adapter.rest.dto.PodSweepRequest;
import com.krizaka.sovkora.job.infrastructure.adapter.rest.dto.PodTokenRequest;
import com.krizaka.sovkora.job.infrastructure.adapter.rest.dto.PodTokenResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the GPU launcher gets, and ends, a RunPod pod's own job service token
 * (krizaka-internal#3645).
 *
 * <p>{@code /internal/v1}: the launcher calls it through the relay with a {@code SERVICE} token
 * that stays on the Mac; the pod only ever holds the token minted here, which this surface does not
 * accept.
 */
@RestController
@RequestMapping("/internal/v1/gpu-pods")
class PodTokenController {

  private final PodTokenService podTokenService;

  PodTokenController(PodTokenService podTokenService) {
    this.podTokenService = podTokenService;
  }

  /**
   * Mints the token of a pod about to start; a token the pod held before is revoked.
   *
   * @param podKey the pod's key
   * @param request its lifetime cap
   * @return the token, its id and its expiry
   */
  @PostMapping("/{podKey}/token")
  @ResponseStatus(HttpStatus.CREATED)
  PodTokenResponse mint(@PathVariable String podKey, @RequestBody PodTokenRequest request) {
    return PodTokenResponse.of(
        podTokenService.mint(new PodKey(podKey), Duration.ofSeconds(request.lifetimeSeconds())));
  }

  /**
   * Revokes the token of a pod the launcher terminated.
   *
   * @param podKey the pod's key
   * @return the pod, when it held a live token
   */
  @DeleteMapping("/{podKey}/token")
  PodRevocationResponse revoke(@PathVariable String podKey) {
    PodKey pod = new PodKey(podKey);
    return new PodRevocationResponse(
        podTokenService.revoke(pod) > 0 ? List.of(pod.value()) : List.of());
  }

  /**
   * Revokes the token of every pod that no longer runs.
   *
   * @param request the pods that still run
   * @return the pods whose token was revoked
   */
  @PostMapping("/tokens/sweep")
  PodRevocationResponse sweep(@RequestBody PodSweepRequest request) {
    List<PodKey> running = request.running().stream().map(PodKey::new).toList();
    return new PodRevocationResponse(
        podTokenService.sweep(running).stream().map(PodKey::value).toList());
  }

  /** A malformed pod key or lifetime is the caller's error, never a 500. */
  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail malformed(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
