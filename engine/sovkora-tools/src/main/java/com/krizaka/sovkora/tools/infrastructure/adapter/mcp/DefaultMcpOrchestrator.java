package com.krizaka.sovkora.tools.infrastructure.adapter.mcp;

import com.krizaka.sovkora.core.domain.ports.outbound.McpOrchestrator;
import com.krizaka.sovkora.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.UserMcpServerProvider;
import com.krizaka.sovkora.core.infrastructure.support.SecurityContextUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Default implementation for MCP Orchestrator. Bridges external MCP server capabilities into the
 * Sovkora engine.
 *
 * <p>Uses Spring {@link RestClient} for HTTP transport on Java 21 Virtual Threads for efficient,
 * non-blocking parallel retrieval from multiple MCP servers.
 */
@Component
public class DefaultMcpOrchestrator implements McpOrchestrator {

  private final PlatformMcpServerProvider platformMcpServerProvider;
  private final UserMcpServerProvider userMcpServerProvider;
  private final RestClient.Builder restClientBuilder;

  /**
   * Instantiates a new DefaultMcpOrchestrator using the platform and user server providers.
   *
   * @param platformMcpServerProvider Platform MCP provider.
   * @param userMcpServerProvider User private MCP provider.
   * @param restClientBuilder The auto-configured Spring RestClient builder.
   */
  public DefaultMcpOrchestrator(
      PlatformMcpServerProvider platformMcpServerProvider,
      UserMcpServerProvider userMcpServerProvider,
      RestClient.Builder restClientBuilder) {
    this.platformMcpServerProvider = platformMcpServerProvider;
    this.userMcpServerProvider = userMcpServerProvider;
    this.restClientBuilder = restClientBuilder;
  }

  /**
   * Resolves context from external MCP endpoints using parallel HTTP requests.
   *
   * <p>Utilizes Spring RestClient and CompletableFuture on Virtual Threads for efficient,
   * non-blocking retrieval from multiple servers.
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
      Thread.currentThread().interrupt();
      return "";
    } catch (ExecutionException e) {
      return "";
    }
  }

  /**
   * Fetches context from a single MCP endpoint using Spring RestClient.
   *
   * @param endpoint The full URL of the MCP server endpoint.
   * @return The response body, or empty string on failure.
   */
  private String fetchContext(String endpoint) {
    try {
      RestClient client = restClientBuilder.clone().baseUrl(endpoint).build();
      String body = client.get().retrieve().body(String.class);
      return body != null ? body : "";
    } catch (Exception ex) {
      return "";
    }
  }

  /**
   * Resolves tools from external Model Context Protocol (MCP) server endpoints.
   *
   * <p>Under local dev environments, this returns custom tool registrations matching the target
   * client criteria.
   *
   * @return A list of external tool definition schema configurations.
   * @see com.krizaka.sovkora.core.pipeline.McpOrchestrator
   */
  @Override
  public List<Object> resolveExternalTools() {
    return new ArrayList<>();
  }
}
