package com.krizaka.sovkora.automationservice.infrastructure.adapter.studio;

import com.krizaka.security.jwt.SessionJwtProperties;
import com.krizaka.security.token.ServiceTokenProvider;
import com.krizaka.sovkora.automationservice.domain.exception.StudioStartRefusedException;
import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.port.StudioRunStarter;
import com.krizaka.sovkora.automationservice.infrastructure.config.StudioClientProperties;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Starts and reads Studio runs over the Studio service's REST surface, <b>as the automation's
 * owner</b>: the token is minted per call with the platform's own provider ({@code
 * krizaka-security}, subject = the owner), exactly the trust ADR-035 already states — the boundary
 * is the shared secret, which this service holds like every other. The run takes its credit hold
 * and its entitlement check like a run the owner clicked.
 *
 * <p>Refusals keep a stable code and a few words, never the response body.
 */
@Component
class HttpStudioRunStarterAdapter implements StudioRunStarter {

  private static final Logger logger = LoggerFactory.getLogger(HttpStudioRunStarterAdapter.class);
  private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

  private final RestClient studio;
  private final String secret;

  HttpStudioRunStarterAdapter(StudioClientProperties properties, SessionJwtProperties jwt) {
    Objects.requireNonNull(properties, "StudioClientProperties cannot be null");
    this.secret = Objects.requireNonNull(jwt, "SessionJwtProperties cannot be null").secret();
    SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(properties.connectTimeout());
    requests.setReadTimeout(properties.readTimeout());
    this.studio =
        RestClient.builder()
            .baseUrl(properties.baseUrl().toString())
            .requestFactory(requests)
            .build();
  }

  @Override
  public UUID start(
      String ownerId, String studioKey, Map<String, Object> inputs, String idempotencyKey) {
    try {
      JsonNode accepted =
          studio
              .post()
              .uri("/api/v1/studios/{key}/runs", studioKey)
              .headers(headers -> headers.setBearerAuth(token(ownerId)))
              .header(IDEMPOTENCY_KEY, idempotencyKey)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("inputs", inputs == null ? Map.of() : inputs))
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  (request, response) -> {
                    throw new StudioStartRefusedException(
                        "studio_refused_" + response.getStatusCode().value(),
                        "the engine refused the run (" + response.getStatusCode().value() + ")");
                  })
              .body(JsonNode.class);
      if (accepted == null || !accepted.hasNonNull("id")) {
        throw new StudioStartRefusedException(
            "studio_no_run", "the engine accepted the run but returned none");
      }
      return UUID.fromString(accepted.get("id").asString());
    } catch (RestClientException unreachable) {
      logger.warn(
          "the Studio service could not be reached: {}", unreachable.getClass().getSimpleName());
      throw new StudioStartRefusedException(
          "studio_unreachable", "the engine could not be reached");
    }
  }

  @Override
  public Optional<RunStatus> endedStatus(String ownerId, UUID studioRunId) {
    try {
      JsonNode run =
          studio
              .get()
              .uri("/api/v1/studios/runs/{id}", studioRunId)
              .headers(headers -> headers.setBearerAuth(token(ownerId)))
              .retrieve()
              .body(JsonNode.class);
      if (run == null) {
        return Optional.empty();
      }
      return switch (run.path("status").asString("")) {
        case "SUCCEEDED" -> Optional.of(RunStatus.SUCCEEDED);
        case "FAILED", "CANCELLED", "REJECTED" -> Optional.of(RunStatus.FAILED);
        default -> Optional.empty();
      };
    } catch (RestClientException unreadable) {
      // A run that cannot be read is not a run that failed: the next sweep tries again.
      return Optional.empty();
    }
  }

  private String token(String ownerId) {
    return new ServiceTokenProvider(secret, ownerId).token();
  }
}
