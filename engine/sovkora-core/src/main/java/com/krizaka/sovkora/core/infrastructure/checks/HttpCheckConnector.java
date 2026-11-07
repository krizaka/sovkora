package com.krizaka.sovkora.core.infrastructure.checks;

import com.krizaka.sovkora.core.domain.model.checks.*;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckHttp;
import com.krizaka.sovkora.core.domain.ports.outbound.ExternalCheckConnector;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the HTTP adapters of the providers share (sovkora-engine#73): fail closed without an HTTPS
 * endpoint or a key, one POST per search, the provider's answer reduced to an outcome, a closed
 * category and an opaque reference, and nothing of the subject or of the provider's message kept.
 *
 * <p><b>The wire format is provisional.</b> SOQUIJ, Trustii and Certn publish no API we may rely on
 * (the design note, section 6): the request and answer shapes below are the engine's own neutral
 * contract, to be mapped onto each provider's real documentation when the account is opened and
 * before any pilot call. The tests run on recorded answers of this shape, never on a live API.
 *
 * <p>Status mapping: {@code 2xx} reads the body ({@code status} is {@code match}, {@code no_match}
 * or {@code pending}); anything else, an unreadable body or an unknown status is a {@code
 * PROVIDER_ERROR}; a slow provider is a {@code TIMEOUT}.
 */
public final class HttpCheckConnector implements ExternalCheckConnector {

  private static final JsonMapper JSON = new JsonMapper();

  private final String provider;
  private final Map<CheckPurpose, String> paths;
  private final String authScheme;
  private final CheckHttp http;
  private final Duration timeout;

  private HttpCheckConnector(
      String provider,
      Map<CheckPurpose, String> paths,
      String authScheme,
      CheckHttp http,
      Duration timeout) {
    this.provider = provider;
    this.paths = Map.copyOf(paths);
    this.authScheme = authScheme;
    this.http = Objects.requireNonNull(http, "http must not be null");
    this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
  }

  /**
   * SOQUIJ, Service Web Plumitifs: court records of Québec, the TAL and the civil and penal
   * plumitifs. Its web service is not public: endpoint, key and wire mapping are confirmed when the
   * account is opened.
   *
   * @param http the HTTP door
   * @param timeout how long a search may take
   * @return the connector, handler key {@code soquij}
   */
  public static HttpCheckConnector soquij(CheckHttp http, Duration timeout) {
    return new HttpCheckConnector(
        "soquij", Map.of(CheckPurpose.COURT_RECORDS, "/plumitifs"), "Bearer", http, timeout);
  }

  /**
   * Trustii: credit (a bureau behind it) and identity. Its API, bureau and consent wording are
   * confirmed when the account is opened.
   *
   * @param http the HTTP door
   * @param timeout how long a search may take
   * @return the connector, handler key {@code trustii}
   */
  public static HttpCheckConnector trustii(CheckHttp http, Duration timeout) {
    return new HttpCheckConnector(
        "trustii",
        Map.of(CheckPurpose.CREDIT, "/credit", CheckPurpose.IDENTITY, "/identity"),
        "Bearer",
        http,
        timeout);
  }

  /**
   * Certn: the fallback outside Québec, court records and identity. CertnCentric's authentication
   * and webhook signing are confirmed in writing when the account is opened.
   *
   * @param http the HTTP door
   * @param timeout how long a search may take
   * @return the connector, handler key {@code certn}
   */
  public static HttpCheckConnector certn(CheckHttp http, Duration timeout) {
    return new HttpCheckConnector(
        "certn",
        Map.of(CheckPurpose.COURT_RECORDS, "/court-records", CheckPurpose.IDENTITY, "/identity"),
        "Api-Key",
        http,
        timeout);
  }

  @Override
  public String provider() {
    return provider;
  }

  /** The purposes this provider serves. */
  Set<CheckPurpose> supports() {
    return paths.keySet();
  }

  private Map<String, String> authHeaders(String key) {
    return Map.of("Authorization", authScheme + " " + key, "Content-Type", "application/json");
  }

  private String path(CheckPurpose purpose) {
    return paths.get(purpose);
  }

  @Override
  public CheckResult verifyCourtRecords(CheckRequest request) {
    return search(request, CheckPurpose.COURT_RECORDS);
  }

  @Override
  public CheckResult verifyCredit(CheckRequest request) {
    return search(request, CheckPurpose.CREDIT);
  }

  @Override
  public CheckResult verifyIdentity(CheckRequest request) {
    return search(request, CheckPurpose.IDENTITY);
  }

  @Override
  public CheckResult poll(CheckRequest request, CheckPurpose purpose, String providerRef) {
    String base = endpoint(request, purpose);
    return send(
        base + path(purpose) + "/" + encode(providerRef), request, purpose, Map.of("poll", true));
  }

  private CheckResult search(CheckRequest request, CheckPurpose purpose) {
    String base = endpoint(request, purpose);
    CheckSubject subject = request.subject();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("lastName", subject.lastName());
    body.put("firstName", subject.firstName());
    if (subject.birthDate() != null) {
      body.put("birthDate", subject.birthDate().toString());
    }
    return send(base + path(purpose), request, purpose, body);
  }

  private String endpoint(CheckRequest request, CheckPurpose purpose) {
    if (!supports().contains(purpose)) {
      throw new UnsupportedOperationException(provider() + " does not serve " + purpose);
    }
    String endpoint = request.endpoint();
    if (endpoint == null || !endpoint.startsWith("https://")) {
      throw new ProviderNotConfiguredException(provider() + " has no HTTPS endpoint");
    }
    String key = request.key().value();
    if (key == null || key.isBlank()) {
      throw new ProviderNotConfiguredException(provider() + " has no key");
    }
    return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
  }

  private CheckResult send(
      String url, CheckRequest request, CheckPurpose purpose, Map<String, Object> body) {
    CheckHttp.Reply reply;
    try {
      reply =
          http.post(
              url, authHeaders(request.key().value()), JSON.writeValueAsString(body), timeout);
    } catch (CheckHttp.TimeoutException slow) {
      return CheckResult.answered(CheckOutcome.TIMEOUT, null);
    }
    if (reply == null || reply.status() < 200 || reply.status() >= 300) {
      return CheckResult.answered(CheckOutcome.PROVIDER_ERROR, null);
    }
    return read(reply.body(), purpose);
  }

  private static CheckResult read(String body, CheckPurpose purpose) {
    try {
      JsonNode node = JSON.readTree(body);
      String status = node.path("status").asString("").toLowerCase(Locale.ROOT);
      String reference = node.path("reference").asString(null);
      return switch (status) {
        case "match" ->
            CheckResult.answered(
                CheckOutcome.MATCH,
                purpose == CheckPurpose.COURT_RECORDS
                    ? category(node.path("category").asString(""))
                    : CheckCategory.NONE,
                reference);
        case "no_match" -> CheckResult.answered(CheckOutcome.NO_MATCH, reference);
        case "pending" ->
            reference == null || reference.isBlank()
                ? CheckResult.answered(CheckOutcome.PROVIDER_ERROR, null)
                : CheckResult.answered(CheckOutcome.PENDING, reference);
        default -> CheckResult.answered(CheckOutcome.PROVIDER_ERROR, null);
      };
    } catch (RuntimeException unreadable) {
      // The body may carry the subject: only the outcome is kept.
      return CheckResult.answered(CheckOutcome.PROVIDER_ERROR, null);
    }
  }

  /** A category outside the closed set reads as {@code NONE}. */
  private static CheckCategory category(String wire) {
    try {
      return CheckCategory.valueOf(wire.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      return CheckCategory.NONE;
    }
  }

  private static String encode(String reference) {
    return java.net.URLEncoder.encode(reference, java.nio.charset.StandardCharsets.UTF_8);
  }
}
