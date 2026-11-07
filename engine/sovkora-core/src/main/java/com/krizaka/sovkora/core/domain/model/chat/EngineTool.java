package com.krizaka.sovkora.core.domain.model.chat;

import java.util.Objects;
import java.util.function.Function;

/**
 * One tool the models may call, as the engine sees it, in pure Java (ADR-092 §4 rule 1): its name,
 * what it does, the JSON schema of its input and how to run it.
 *
 * <p>A tool that came from a library keeps it in {@code nativeTool} so that leaving the engine
 * loses nothing (caching wrappers, provider metadata); only the adapter at the edge reads it back.
 * Code outside that edge treats it as opaque.
 *
 * @param name the tool's name, unique in a registry
 * @param description what the tool does, for the model
 * @param inputSchema the JSON schema of the arguments
 * @param execution runs the tool: JSON arguments in, text out
 * @param nativeTool the library tool this one stands for, or {@code null}
 */
public record EngineTool(
    String name,
    String description,
    String inputSchema,
    Function<String, String> execution,
    Object nativeTool) {

  /** Compact constructor — a name and an execution are required. */
  public EngineTool {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(execution, "execution must not be null");
    description = description == null ? "" : description;
    inputSchema = inputSchema == null ? "" : inputSchema;
  }

  /**
   * Runs the tool.
   *
   * @param argumentsJson the arguments as JSON
   * @return the tool's answer
   */
  public String call(String argumentsJson) {
    return execution.apply(argumentsJson);
  }
}
