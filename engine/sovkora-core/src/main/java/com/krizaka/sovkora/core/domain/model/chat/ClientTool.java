package com.krizaka.sovkora.core.domain.model.chat;

import java.util.Objects;

/**
 * A tool the <b>client</b> runs (ADR-093 §4): the engine offers it to the model and hands the
 * model's call back to the client; it never runs it. Only the declaration travels: the name, what
 * it does for the model, the JSON schema of its input.
 *
 * @param name the tool's name, as the client registers it
 * @param description what the tool does, for the model
 * @param inputSchema the JSON schema of the arguments
 */
public record ClientTool(String name, String description, String inputSchema) {

  /** Compact constructor: a name is required, a missing text is empty. */
  public ClientTool {
    Objects.requireNonNull(name, "name must not be null");
    description = description == null ? "" : description;
    inputSchema = inputSchema == null || inputSchema.isBlank() ? "{}" : inputSchema;
  }
}
