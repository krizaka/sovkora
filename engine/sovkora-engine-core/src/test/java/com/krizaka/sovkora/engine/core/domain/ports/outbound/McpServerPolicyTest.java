package com.krizaka.sovkora.engine.core.domain.ports.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.engine.core.domain.ports.outbound.McpServerPolicy.Refusal;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.McpServerPolicy.StdioLaunch;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** An engine with no policy wired opens no MCP server; a local declaration names its command. */
class McpServerPolicyTest {

  @Test
  void theClosedPolicyRefusesEveryEndpointAndDeclaresNoLocalServer() {
    McpServerPolicy closed = McpServerPolicy.closed();

    assertThat(closed.refusal("https://mcp.example/sse")).contains(Refusal.ADDRESS);
    assertThat(closed.stdioLaunch("filesystem")).isEmpty();
  }

  @Test
  void aLocalDeclarationNamesItsCommand() {
    assertThatThrownBy(() -> new StdioLaunch(" ", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StdioLaunch(null, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aLocalDeclarationKeepsItsArgumentsAsDeclared() {
    List<String> args = new ArrayList<>(List.of("-y", "server"));
    StdioLaunch launch = new StdioLaunch("npx", args);
    args.add("--extra");

    assertThat(launch.args()).containsExactly("-y", "server");
    assertThat(new StdioLaunch("npx", null).args()).isEmpty();
  }
}
