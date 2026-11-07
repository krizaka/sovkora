package com.krizaka.sovkora.adapter.springai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.application.security.ActorScope;
import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class SpringAiToolRegistryTest {

  private static final Actor USER = Actor.of("user-1", List.of("ROLE_USER")).orElseThrow();

  record Echo(String text) {}

  @Test
  @DisplayName("a tool runs as the actor it was handed to, and is refused to anyone else")
  void toolRunsAsItsActorAndIsRefusedToOthers() {
    SpringAiToolRegistry registry = new SpringAiToolRegistry(null, null, null);
    registry.registerTool(
        "whoami",
        "Names the caller",
        Echo.class,
        in -> ActorScope.current().map(Actor::subject).orElse("nobody"),
        Set.of("ROLE_HR"));
    Actor hr = Actor.of("hr-1", List.of("ROLE_HR")).orElseThrow();

    assertThat(registry.getRegisteredTools(USER)).isEmpty();
    assertThat(registry.getRegisteredTools(null)).isEmpty();
    assertThatThrownBy(() -> registry.authorize(USER, "whoami"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> registry.authorize(null, "whoami"))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(registry.authorize(hr, "whoami").call("{\"text\":\"x\"}")).contains("hr-1");
  }

  @Test
  @DisplayName(
      "a registered function comes back as an engine tool that runs and keeps its callback")
  void registeredFunctionIsAnEngineTool() {
    SpringAiToolRegistry registry = new SpringAiToolRegistry(null, null, null);
    registry.registerTool(
        "echo", "Repeats the text", Echo.class, in -> "heard " + in.text(), Set.of("ROLE_USER"));

    List<EngineTool> tools = registry.getRegisteredTools(USER);

    assertThat(tools)
        .singleElement()
        .satisfies(
            tool -> {
              assertThat(tool.name()).isEqualTo("echo");
              assertThat(tool.description()).isEqualTo("Repeats the text");
              assertThat(tool.inputSchema()).contains("text");
              assertThat(tool.nativeTool()).isInstanceOf(ToolCallback.class);
              assertThat(tool.call("{\"text\":\"hi\"}")).contains("heard hi");
            });
  }
}
