package com.krizaka.sovkora.engine.tools.infrastructure.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.engine.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.PlatformMcpServerProvider.PlatformMcpServer;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.UserMcpServerProvider;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.UserMcpServerProvider.UserMcpServer;
import com.krizaka.sovkora.engine.core.infrastructure.support.SecurityContextUtil;
import com.krizaka.sovkora.engine.tools.domain.model.research.FetchLimits;
import com.krizaka.sovkora.engine.tools.domain.model.research.FetchRefusal;
import com.krizaka.sovkora.engine.tools.domain.port.HostResolver;
import com.krizaka.sovkora.engine.tools.domain.port.SafeHttp;
import com.krizaka.sovkora.engine.tools.infrastructure.adapter.http.PinnedHttpClient;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

/** The MCP context is read through the client that cannot be pointed at the inside. */
class DefaultMcpOrchestratorTest {

  private static final String USER = "user-1";

  private final PlatformMcpServerProvider platform = mock(PlatformMcpServerProvider.class);
  private final UserMcpServerProvider users = mock(UserMcpServerProvider.class);

  /** A resolver that answers from a table and records every host it was asked for. */
  private static final class TableResolver implements HostResolver {
    private final Map<String, String> table;
    private final List<String> asked = new CopyOnWriteArrayList<>();

    TableResolver(Map<String, String> table) {
      this.table = table;
    }

    @Override
    public List<InetAddress> resolve(String host) throws UnknownHostException {
      asked.add(host);
      String literal = table.get(host);
      if (literal != null) {
        return List.of(InetAddress.getByName(literal));
      }
      if (host.chars().allMatch(c -> Character.digit(c, 16) >= 0 || ".:[]".indexOf(c) >= 0)) {
        // An address literal resolves to itself, as the system resolver does.
        return List.of(InetAddress.getByName(host));
      }
      throw new UnknownHostException(host);
    }
  }

  /** A client that answers 200 with a fixed body and records each call. */
  private static final class AnsweringHttp implements SafeHttp {
    private final List<FetchLimits> limits = new ArrayList<>();
    private final List<Function<URI, Optional<FetchRefusal>>> hopChecks = new ArrayList<>();
    private final int status;

    AnsweringHttp(int status) {
      this.status = status;
    }

    @Override
    public Outcome get(
        URI uri, FetchLimits fetchLimits, Function<URI, Optional<FetchRefusal>> hop) {
      limits.add(fetchLimits);
      hopChecks.add(hop);
      Optional<FetchRefusal> refusal = hop.apply(uri);
      if (refusal.isPresent()) {
        return new Outcome(Optional.empty(), refusal);
      }
      byte[] body = "context-data".getBytes(StandardCharsets.UTF_8);
      return new Outcome(
          Optional.of(new Response(uri, status, "text/plain", "UTF-8", body, null, null)),
          Optional.empty());
    }
  }

  private String contextFor(SafeHttp http, String userUrl) {
    when(platform.getActivePlatformMcpServers()).thenReturn(List.of());
    when(users.getActiveUserMcpServers(USER))
        .thenReturn(List.of(new UserMcpServer(1, USER, "u", userUrl, null, true)));
    try (MockedStatic<SecurityContextUtil> security = mockStatic(SecurityContextUtil.class)) {
      security
          .when(SecurityContextUtil::extractSecurityMetadata)
          .thenReturn(Map.of("userId", USER));
      return new DefaultMcpOrchestrator(platform, users, http).resolveExternalContext();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://127.0.0.1/mcp",
        "https://10.0.0.5/mcp",
        "https://192.168.1.10/mcp",
        "https://169.254.169.254/latest",
        "https://[::1]/mcp",
        "https://inside.example/mcp",
        "http://outside.example/mcp",
        "file:///etc/hosts"
      })
  @DisplayName("a user's endpoint on the inside, or not over https, is never read")
  void refusesTheInside(String url) {
    var resolver =
        new TableResolver(Map.of("inside.example", "10.1.2.3", "outside.example", "1.1.1.1"));

    String context = contextFor(new PinnedHttpClient(resolver), url);

    assertThat(context).isEmpty();
    if (url.startsWith("https://")) {
      // Refused after the address check, not by a failed lookup.
      assertThat(resolver.asked).hasSize(1);
    }
  }

  @Test
  @DisplayName("a public https endpoint is read, with no redirect allowed and https on every hop")
  void readsAPublicEndpoint() {
    var http = new AnsweringHttp(200);

    String context = contextFor(http, "https://mcp.example/context");

    assertThat(context).isEqualTo("context-data");
    assertThat(http.limits).singleElement().extracting(FetchLimits::maxRedirects).isEqualTo(0);
    assertThat(http.hopChecks.getFirst().apply(URI.create("http://mcp.example/x")))
        .contains(FetchRefusal.SCHEME);
  }

  @Test
  @DisplayName("an error answer adds nothing to the prompt")
  void errorStatusAddsNothing() {
    assertThat(contextFor(new AnsweringHttp(500), "https://mcp.example/context")).isEmpty();
  }

  @Test
  @DisplayName("a URL that does not parse adds nothing and fetches nothing")
  void malformedUrlAddsNothing() {
    var http = new AnsweringHttp(200);

    assertThat(contextFor(http, "https://exa mple/^")).isEmpty();
    assertThat(http.limits).isEmpty();
  }

  @Test
  @DisplayName("platform REMOTE servers and the user's own are both read")
  void readsPlatformAndUserServers() {
    when(platform.getActivePlatformMcpServers())
        .thenReturn(
            List.of(
                new PlatformMcpServer(
                    1, "p", "REMOTE", "https://platform.example/mcp", null, null, null, true),
                new PlatformMcpServer(2, "local", "LOCAL", null, "echo", null, null, true)));
    when(users.getActiveUserMcpServers(USER))
        .thenReturn(
            List.of(new UserMcpServer(1, USER, "u", "https://user.example/mcp", null, true)));
    var http = new AnsweringHttp(200);

    try (MockedStatic<SecurityContextUtil> security = mockStatic(SecurityContextUtil.class)) {
      security
          .when(SecurityContextUtil::extractSecurityMetadata)
          .thenReturn(Map.of("userId", USER));
      String context = new DefaultMcpOrchestrator(platform, users, http).resolveExternalContext();

      assertThat(context).isEqualTo("context-data\ncontext-data");
    }
  }

  @Test
  @DisplayName("no server, no context")
  void noEndpointsReturnsEmpty() {
    when(platform.getActivePlatformMcpServers()).thenReturn(List.of());

    assertThat(
            new DefaultMcpOrchestrator(platform, users, new AnsweringHttp(200))
                .resolveExternalContext())
        .isEmpty();
  }

  @Test
  @DisplayName("no platform provider is handled")
  void nullPlatformProvider() {
    assertThat(
            new DefaultMcpOrchestrator(null, users, new AnsweringHttp(200))
                .resolveExternalContext())
        .isEmpty();
  }

  @Test
  @DisplayName("the orchestrator resolves no external tool")
  void resolveExternalToolsIsEmpty() {
    assertThat(
            new DefaultMcpOrchestrator(platform, users, new AnsweringHttp(200))
                .resolveExternalTools())
        .isEmpty();
  }
}
