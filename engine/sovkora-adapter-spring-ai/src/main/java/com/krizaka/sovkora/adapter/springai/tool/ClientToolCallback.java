package com.krizaka.sovkora.adapter.springai.tool;

import com.krizaka.sovkora.core.domain.model.chat.ClientTool;
import java.util.Objects;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * A tool the client runs (ADR-093 §4), offered to the model by its declaration alone. It has no
 * behaviour on the engine: if anything asked it to run, it would refuse, so a client tool can never
 * be executed here, whatever the model's loop does.
 *
 * @param tool the client's declaration
 */
public record ClientToolCallback(ClientTool tool) implements ToolCallback {

  /** Compact constructor. */
  public ClientToolCallback {
    Objects.requireNonNull(tool, "tool");
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return ToolDefinition.builder()
        .name(tool.name())
        .description(tool.description())
        .inputSchema(tool.inputSchema())
        .build();
  }

  @Override
  public String call(String toolInput) {
    throw new IllegalStateException(
        "Tool '" + tool.name() + "' is executed by the client, never by the engine");
  }
}
