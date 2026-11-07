package com.krizaka.sovkora.core.domain.model.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EngineToolTest {

  @Test
  @DisplayName("a tool runs its execution with the JSON arguments")
  void callRunsTheExecution() {
    EngineTool tool = new EngineTool("echo", "d", "{}", json -> "got " + json, null);

    assertThat(tool.call("{\"a\":1}")).isEqualTo("got {\"a\":1}");
  }

  @Test
  @DisplayName("missing description and schema are empty; name and execution are required")
  void defaultsAndRequirements() {
    EngineTool tool = new EngineTool("echo", null, null, json -> "", null);

    assertThat(tool.description()).isEmpty();
    assertThat(tool.inputSchema()).isEmpty();
    assertThatThrownBy(() -> new EngineTool(null, "", "", json -> "", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new EngineTool("x", "", "", null, null))
        .isInstanceOf(NullPointerException.class);
  }
}
