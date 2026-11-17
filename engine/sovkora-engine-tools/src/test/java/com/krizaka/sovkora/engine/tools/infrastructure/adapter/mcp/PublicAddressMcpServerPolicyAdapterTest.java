package com.krizaka.sovkora.engine.tools.infrastructure.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.engine.core.domain.ports.outbound.McpServerPolicy.Refusal;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.McpServerPolicy.StdioLaunch;
import com.krizaka.sovkora.engine.tools.domain.port.HostResolver;
import com.krizaka.sovkora.engine.tools.infrastructure.config.McpProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Which MCP servers the engine may open: public https endpoints, declared local commands. */
class PublicAddressMcpServerPolicyAdapterTest {

  private static final HostResolver RESOLVER =
      host ->
          switch (host) {
            case "mcp.example" -> List.of(InetAddress.getByName("1.1.1.1"));
            case "inside.example" -> List.of(InetAddress.getByName("10.1.2.3"));
            case "mixed.example" ->
                List.of(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("127.0.0.1"));
            default ->
                host.chars().allMatch(c -> Character.isDigit(c) || c == '.' || c == ':')
                    ? List.of(InetAddress.getByName(host))
                    : throwUnknown(host);
          };

  private static List<InetAddress> throwUnknown(String host) throws UnknownHostException {
    throw new UnknownHostException(host);
  }

  private final PublicAddressMcpServerPolicyAdapter policy =
      new PublicAddressMcpServerPolicyAdapter(
          RESOLVER,
          new McpProperties(
              Map.of(
                  "filesystem",
                  new McpProperties.StdioCommand("npx", List.of("-y", "mcp-server-filesystem")),
                  "blank",
                  new McpProperties.StdioCommand(" ", null))));

  @ParameterizedTest
  @CsvSource({
    "https://127.0.0.1/mcp, ADDRESS",
    "https://10.0.0.5/mcp, ADDRESS",
    "https://172.16.0.1/mcp, ADDRESS",
    "https://169.254.169.254/latest, ADDRESS",
    "https://inside.example/mcp, ADDRESS",
    "https://mixed.example/mcp, ADDRESS",
    "http://mcp.example/mcp, SCHEME",
    "ftp://mcp.example/mcp, SCHEME",
    "//mcp.example/mcp, SCHEME",
    "https://unknown.example/mcp, UNRESOLVED",
    "https://user@mcp.example/mcp, MALFORMED",
    "not a url, MALFORMED",
    "mcp.example, MALFORMED"
  })
  @DisplayName("an endpoint on the inside, or not over https, is refused with its reason")
  void refuses(String url, Refusal reason) {
    assertThat(policy.refusal(url)).contains(reason);
  }

  @Test
  @DisplayName("a public https endpoint may be reached")
  void admitsAPublicHttpsEndpoint() {
    assertThat(policy.refusal("https://mcp.example/sse")).isEmpty();
    assertThat(policy.refusal("HTTPS://mcp.example/sse")).isEmpty();
  }

  @Test
  @DisplayName("no URL is refused, never an error")
  void noUrlIsRefused() {
    assertThat(policy.refusal(null)).contains(Refusal.MALFORMED);
  }

  @Test
  @DisplayName("a declared id launches its declared command and arguments")
  void declaredIdLaunches() {
    assertThat(policy.stdioLaunch("filesystem"))
        .contains(new StdioLaunch("npx", List.of("-y", "mcp-server-filesystem")));
  }

  @Test
  @DisplayName("a path, an undeclared id or a declaration without command launches nothing")
  void undeclaredLaunchesNothing() {
    assertThat(policy.stdioLaunch("/bin/sh")).isEmpty();
    assertThat(policy.stdioLaunch("npx")).isEmpty();
    assertThat(policy.stdioLaunch("blank")).isEmpty();
    assertThat(policy.stdioLaunch(null)).isEmpty();
  }

  @Test
  @DisplayName("with nothing declared, no local server is ever started")
  void nothingDeclaredByDefault() {
    var closed = new PublicAddressMcpServerPolicyAdapter(RESOLVER, new McpProperties(null));

    assertThat(closed.stdioLaunch("filesystem")).isEmpty();
  }
}
