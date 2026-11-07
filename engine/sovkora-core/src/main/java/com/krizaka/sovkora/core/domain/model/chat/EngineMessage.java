package com.krizaka.sovkora.core.domain.model.chat;

import java.util.Objects;

/**
 * One message of a conversation as the engine sees it, in pure Java: who speaks and what is said.
 * Interceptors and ports read and write this type, never a model library's (ADR-092 §4 rule 1).
 *
 * <p>A message that came from a library keeps it in {@code nativeMessage} (media, tool answers:
 * what a role and a text cannot say) so that leaving the engine loses nothing; only the adapter at
 * the edge reads it back. Code outside that edge treats it as opaque.
 *
 * @param role who speaks
 * @param text what is said (empty rather than {@code null})
 * @param nativeMessage the library message this one stands for, or {@code null}
 */
public record EngineMessage(Role role, String text, Object nativeMessage) {

  /** Who speaks. */
  public enum Role {
    /** Instructions to the model. */
    SYSTEM,
    /** The person. */
    USER,
    /** The model. */
    ASSISTANT,
    /** The answer of a tool the model called. */
    TOOL
  }

  /** Compact constructor — a role is required and a missing text is empty. */
  public EngineMessage {
    Objects.requireNonNull(role, "role must not be null");
    text = text == null ? "" : text;
  }

  /** A message with no library behind it. */
  public EngineMessage(Role role, String text) {
    this(role, text, null);
  }

  /** An instruction to the model. */
  public static EngineMessage system(String text) {
    return new EngineMessage(Role.SYSTEM, text);
  }

  /** A turn of the person. */
  public static EngineMessage user(String text) {
    return new EngineMessage(Role.USER, text);
  }

  /** A turn of the model. */
  public static EngineMessage assistant(String text) {
    return new EngineMessage(Role.ASSISTANT, text);
  }
}
