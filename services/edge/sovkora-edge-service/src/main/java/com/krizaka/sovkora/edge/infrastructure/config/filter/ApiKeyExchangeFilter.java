package com.krizaka.sovkora.edge.infrastructure.config.filter;

import com.krizaka.security.token.ServiceTokenProvider;
import com.krizaka.sovkora.edge.infrastructure.config.EdgeIdentityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Exchanges inbound {@code oz_} API keys for session JWTs at the facade (Phase 2 cutover): the edge
 * calls the identity service's internal exchange endpoint and rewrites the {@code Authorization}
 * header, so downstream services only ever see JWTs. An invalid key is rejected here with 401 — it
 * never reaches a backend.
 *
 * <p>A key of another environment is refused here too (krizaka-internal#2132): the edge serves one
 * environment ({@code AUTH_API_KEY_ENVIRONMENT}) and a key states its own ({@link
 * ApiKeyEnvironment}), so a dev key never reaches the prod engine. Every refusal is a coded problem
 * ({@link ApiKeyRefusal}).
 *
 * <p>Deliberately uncached: API-key revocation must take effect immediately (the api-keys contract
 * asserts it). A revocation-aware cache can come later with an {@code evt.apikey.revoked}
 * invalidation.
 */
@Component
class ApiKeyExchangeFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(ApiKeyExchangeFilter.class);
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String API_KEY_PREFIX = "oz_";
  private static final String APPLICATION_JSON = "application/json";
  private static final String QUOTA_DETAIL = "The key's calls of this UTC day are spent.";
  private static final String AUTHORIZATION_HEADER = "Authorization";

  private final RestClient identityClient;

  /** Wire shape of the internal exchange response (contract copy — no shared jar). */
  record ExchangeResponse(
      String token,
      List<String> scopes,
      String tierKey,
      Integer requestsPerMinute,
      Integer dailyRequests) {}

  private final ApiKeyDailyQuota quota;
  private final ApiKeyRateLimiter rateLimiter;
  private final ApiKeyEnvironment environment;

  ApiKeyExchangeFilter(RestClient proxyRestClient, EdgeIdentityProperties properties) {
    this(proxyRestClient, properties, ApiKeyDailyQuota.unlimited());
  }

  ApiKeyExchangeFilter(
      RestClient proxyRestClient, EdgeIdentityProperties properties, ApiKeyDailyQuota quota) {
    this(proxyRestClient, properties, quota, ApiKeyRateLimiter.off());
  }

  @Autowired
  ApiKeyExchangeFilter(
      RestClient proxyRestClient,
      EdgeIdentityProperties properties,
      ApiKeyDailyQuota quota,
      ApiKeyRateLimiter rateLimiter) {
    this(
        proxyRestClient,
        properties,
        quota,
        rateLimiter,
        ApiKeyEnvironment.of(properties.apiKeyEnvironment()));
  }

  ApiKeyExchangeFilter(
      RestClient proxyRestClient,
      EdgeIdentityProperties properties,
      ApiKeyDailyQuota quota,
      ApiKeyRateLimiter rateLimiter,
      ApiKeyEnvironment environment) {
    this.environment = environment;
    this.quota = Objects.requireNonNull(quota, "ApiKeyDailyQuota cannot be null");
    this.rateLimiter = Objects.requireNonNull(rateLimiter, "ApiKeyRateLimiter cannot be null");
    Objects.requireNonNull(properties, "EdgeIdentityProperties cannot be null");
    ServiceTokenProvider tokens =
        new ServiceTokenProvider(properties.serviceSecret(), "sovkora-edge-service");
    this.identityClient =
        proxyRestClient
            .mutate()
            .baseUrl(properties.baseUrl().toString())
            // /internal/v1/tokens/exchange is authenticated as of ADR-035. Without this the edge
            // cannot exchange an API key, and every `oz_` caller gets a 401 it cannot explain.
            .requestInitializer(request -> request.getHeaders().setBearerAuth(tokens.token()))
            .build();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader(AUTHORIZATION_HEADER);
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX + API_KEY_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }
    String apiKey = authorization.substring(BEARER_PREFIX.length());
    ExchangeResponse exchanged;
    try {
      exchanged = exchange(apiKey);
    } catch (ResourceAccessException e) {
      logger.warn("API-key exchange unreachable", e);
      ApiKeyRefusal.EXCHANGE_UNREACHABLE.write(
          response, "The identity service cannot exchange the key.");
      return;
    }
    if (exchanged == null) {
      ApiKeyRefusal.INVALID.write(response, "The key is unknown or revoked.");
      return;
    }
    if (!environment.admits(exchanged.scopes())) {
      logger.warn(
          "API key of another environment refused by the {} edge: {} {}",
          environment,
          request.getMethod(),
          request.getRequestURI());
      ApiKeyRefusal.WRONG_ENVIRONMENT.write(
          response, "This key does not belong to the environment of this address.");
      return;
    }
    if (refusedForScopes(request, response, exchanged)) {
      return;
    }
    if (refusedByRateLimit(request, response, apiKey, exchanged)) {
      return;
    }
    if (!quota.tryAcquire(apiKey, exchanged.dailyRequests())) {
      logger.warn(
          "API key over its daily quota: {} {}", request.getMethod(), request.getRequestURI());
      long retryAfter = quota.secondsUntilReset();
      response.setHeader("Retry-After", String.valueOf(retryAfter));
      ApiKeyRefusal.QUOTA_EXCEEDED.write(response, QUOTA_DETAIL, retryAfter);
      return;
    }
    filterChain.doFilter(
        new AuthorizationRewriter(request, BEARER_PREFIX + exchanged.token()), response);
  }

  /**
   * Holds the key to its scopes and hands the product it declares to the proxy; {@code true} when
   * the request was answered: 401 for more than one product or a malformed one, 403 for a route the
   * scopes do not name.
   */
  private boolean refusedForScopes(
      HttpServletRequest request, HttpServletResponse response, ExchangeResponse exchanged)
      throws IOException {
    try {
      ApiKeyProduct.declaredBy(exchanged.scopes())
          .ifPresent(product -> request.setAttribute(ApiKeyProduct.ATTRIBUTE, product.key()));
    } catch (IllegalArgumentException e) {
      logger.warn("API key with an invalid product scope refused: {}", e.getMessage());
      ApiKeyRefusal.PRODUCT_INVALID.write(
          response, "The key declares more than one product, or a malformed one.");
      return true;
    }
    // A scoped key is held to its scopes here: the minted JWT is the owner's, so nothing
    // downstream could tell the difference.
    if (ApiKeyScopes.permits(exchanged.scopes(), request.getMethod(), request.getRequestURI())) {
      return false;
    }
    logger.warn(
        "API key scoped {} refused {} {}",
        exchanged.scopes(),
        request.getMethod(),
        request.getRequestURI());
    ApiKeyRefusal.SCOPE_DENIED.write(response, "The key's scopes do not name this request.");
    return true;
  }

  /** Applies the key's per-minute limit; {@code true} when the request was answered 429. */
  private boolean refusedByRateLimit(
      HttpServletRequest request,
      HttpServletResponse response,
      String apiKey,
      ExchangeResponse exchanged)
      throws IOException {
    if (!rateLimiter.active()) {
      return false;
    }
    ApiKeyRateLimiter.Decision decision =
        rateLimiter.tryAcquire(apiKey, exchanged.requestsPerMinute());
    response.setHeader("RateLimit-Limit", String.valueOf(decision.limit()));
    response.setHeader("RateLimit-Remaining", String.valueOf(decision.remaining()));
    if (decision.overLimit()) {
      logger.warn(
          "API key over its {} requests/minute ({}): {} {}{}",
          decision.limit(),
          exchanged.tierKey(),
          request.getMethod(),
          request.getRequestURI(),
          decision.allowed() ? " — observed, not refused" : "");
    }
    if (!decision.allowed()) {
      response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
      ApiKeyRefusal.RATE_LIMITED.write(
          response, "The key's requests per minute are spent.", decision.retryAfterSeconds());
      return true;
    }
    return false;
  }

  /** Exchanges an API key for a session JWT; {@code null} when the key is invalid/revoked. */
  private ExchangeResponse exchange(String apiKey) {
    ExchangeResponse exchanged =
        identityClient
            .post()
            .uri("/internal/v1/tokens/exchange")
            .header("Content-Type", APPLICATION_JSON)
            .body(Map.of("apiKey", apiKey))
            .exchange(
                (request, response) ->
                    response.getStatusCode().is2xxSuccessful()
                        ? response.bodyTo(ExchangeResponse.class)
                        : null);
    return exchanged != null && exchanged.token() != null ? exchanged : null;
  }

  /** Request wrapper substituting the Authorization header with the minted JWT. */
  private static final class AuthorizationRewriter extends HttpServletRequestWrapper {

    private final String authorization;

    private AuthorizationRewriter(HttpServletRequest request, String authorization) {
      super(request);
      this.authorization = authorization;
    }

    @Override
    public String getHeader(String name) {
      return AUTHORIZATION_HEADER.equalsIgnoreCase(name) ? authorization : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return AUTHORIZATION_HEADER.equalsIgnoreCase(name)
          ? Collections.enumeration(java.util.List.of(authorization))
          : super.getHeaders(name);
    }
  }
}
