package com.krizaka.sovkora.engine.tools.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** The declared local MCP servers bind by id; none is declared unless the deployment says so. */
class McpPropertiesTest {

  private static McpProperties bind(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bindOrCreate("sovkora.mcp", McpProperties.class);
  }

  @Test
  void nothingIsDeclaredByDefault() {
    assertThat(bind(Map.of()).stdio()).isEmpty();
  }

  @Test
  void aDeclarationBindsItsCommandAndArgumentsById() {
    McpProperties properties =
        bind(
            Map.of(
                "sovkora.mcp.stdio.filesystem.command", "npx",
                "sovkora.mcp.stdio.filesystem.args[0]", "-y",
                "sovkora.mcp.stdio.filesystem.args[1]", "mcp-server-filesystem",
                "sovkora.mcp.stdio.time.command", "uvx"));

    assertThat(properties.stdio().get("filesystem").command()).isEqualTo("npx");
    assertThat(properties.stdio().get("filesystem").args())
        .containsExactly("-y", "mcp-server-filesystem");
    assertThat(properties.stdio().get("time").args()).isEmpty();
  }
}
