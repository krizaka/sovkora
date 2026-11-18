package com.krizaka.sovkora.engine.tools.infrastructure.adapter.mcp;

import com.krizaka.sovkora.engine.core.domain.ports.outbound.McpOrchestrator;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.UserMcpServerProvider;
import com.krizaka.sovkora.engine.core.infrastructure.support.SecurityContextUtil;
import com.krizaka.sovkora.engine.tools.domain.model.research.FetchLimits;
import com.krizaka.sovkora.engine.tools.domain.model.research.FetchRefusal;
import com.krizaka.sovkora.engine.tools.domain.port.SafeHttp;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default implementation for MCP Orchestrator. Bridges external MCP server capabilities into the
 * Sovkora engine.
 *
 * <p>Every endpoint is read through {@link SafeHttp}, the client that cannot be pointed at the
 * inside (ADR-087 §4): https only, public addresses only, the connection pinned to the address that
 * was checked, no redirect followed, and one cap on time and size. The servers are read in parallel
 * on virtual threads.
 */
@Component
public class DefaultMcpOrchestrator implements McpOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(DefaultMcpOrchestrator.class);

  private final PlatformMcpServerProvider platformMcpServerProvider;
  private final UserMcpServerProvider userMcpServerProvider;
  private final SafeHttp http;

  /** One MCP context read: no redirect, five seconds, 256 KiB. */
  private static final FetchLimits LIMITS =
      new FetchLimits(
          5_000, 256 * 1024, 0, 1, List.of("application/json", "text/plain"), "SovkoraMcp/1.0", 1);

  /**
   * Instantiates a new DefaultMcpOrchestrator using the platform and user server providers.
   *
   * @param platformMcpServerProvider Platform MCP provider.
   * @param userMcpServerProvider User private MCP provider.
   * @param http The HTTP client that reaches public addresses only.
   */
  public DefaultMcpOrchestrator(
      PlatformMcpServerProvider platformMcpServerProvider,
      UserMcpServerProvider userMcpServerProvider,
      SafeHttp http) {
    this.platformMcpServerProvider = platformMcpServerProvider;
    this.userMcpServerProvider = userMcpServerProvider;
    this.http = http;
  }

  /**
   * Resolves context from external MCP endpoints using parallel HTTP requests, each one through
   * {@link SafeHttp} on a virtual thread.
   *
   * @return A consolidated string containing external context from all reachable servers.
   */
  @Override
  public String resolveExternalContext() {
    List<String> endpoints = new ArrayList<>();
    if (platformMcpServerProvider != null) {
      platformMcpServerProvider.getActivePlatformMcpServers().stream()
          .filter(
              s ->
                  "REMOTE".equalsIgnoreCase(s.transportType())
                      && s.url() != null
                      && !s.url().isBlank())
          .map(PlatformMcpServerProvider.PlatformMcpServer::url)
          .forEach(endpoints::add);
    }
    String currentUserId =
        SecurityContextUtil.extractSecurityMetadata().get("userId") != null
            ? SecurityContextUtil.extractSecurityMetadata().get("userId").toString()
            : null;
    if (userMcpServerProvider != null && currentUserId != null) {
      userMcpServerProvider.getActiveUserMcpServers(currentUserId).stream()
          .filter(s -> s.url() != null && !s.url().isBlank())
          .map(UserMcpServerProvider.UserMcpServer::url)
          .forEach(endpoints::add);
    }

    if (endpoints.isEmpty()) {
      return "";
    }

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<CompletableFuture<String>> futures =
          endpoints.stream()
              .map(
                  endpoint -> CompletableFuture.supplyAsync(() -> fetchContext(endpoint), executor))
              .toList();

      CompletableFuture<?>[] array = futures.toArray(new CompletableFuture<?>[0]);
      return CompletableFuture.allOf(array)
          .thenApply(
              v ->
                  futures.stream()
                      .map(CompletableFuture::join)
                      .filter(s -> !s.isBlank())
                      .collect(Collectors.joining("\n")))
          .get();
    } catch (InterruptedException e) {
      log.debug("MCP context fetch interrupted", e);
      Thread.currentThread().interrupt();
      return "";
    } catch (ExecutionException e) {
      log.warn("MCP context fetch execution failed: {}", e.getMessage(), e);
      return "";
    }
  }

  /**
   * Fetches context from a single MCP endpoint, or nothing when the endpoint is refused or fails.
   *
   * @param endpoint The full URL of the MCP server endpoint.
   * @return The response body, or empty string on failure or refusal.
   */
  private String fetchContext(String endpoint) {
    URI uri;
    try {
      uri = URI.create(endpoint.trim());
    } catch (IllegalArgumentException malformed) {
      // Not the exception: its message repeats the URL.
      log.warn("MCP endpoint refused: {}", FetchRefusal.SCHEME);
      return "";
    }
    SafeHttp.Outcome outcome = http.get(uri, LIMITS, DefaultMcpOrchestrator::httpsOnly);
    if (outcome.refusal().isPresent()) {
      // The reason only: the URL is the user's, and its path may carry a secret.
      log.warn("MCP endpoint refused: {}", outcome.refusal().get());
      return "";
    }
    return outcome
        .response()
        .filter(response -> response.status() >= 200 && response.status() < 300)
        .map(DefaultMcpOrchestrator::text)
        .orElse("");
  }

  private static Optional<FetchRefusal> httpsOnly(URI hop) {
    String scheme = hop.getScheme() == null ? "" : hop.getScheme().toLowerCase(Locale.ROOT);
    return "https".equals(scheme) ? Optional.empty() : Optional.of(FetchRefusal.SCHEME);
  }

  private static String text(SafeHttp.Response response) {
    Charset charset = StandardCharsets.UTF_8;
    try {
      if (response.charset() != null && Charset.isSupported(response.charset())) {
        charset = Charset.forName(response.charset());
      }
    } catch (IllegalArgumentException unknownCharset) {
      log.debug("MCP answer names an unknown charset, read as UTF-8", unknownCharset);
    }
    return new String(response.body(), charset);
  }

  /**
   * Resolves tools from external Model Context Protocol (MCP) server endpoints.
   *
   * <p>Under local dev environments, this returns custom tool registrations matching the target
   * client criteria.
   *
   * @return A list of external tool definition schema configurations.
   * @see com.krizaka.sovkora.engine.core.pipeline.McpOrchestrator
   */
  @Override
  public List<Object> resolveExternalTools() {
    return new ArrayList<>();
  }
}
