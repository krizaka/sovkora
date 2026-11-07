package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import java.util.Optional;

/**
 * The port that stores a tool's answer as text and reads it back (#121): conversation memory keeps
 * the answer's structure (call id, tool name, data) without holding the provider library's message
 * type. Its Spring AI implementation lives in {@code sovkora-adapter-spring-ai}.
 */
public interface ToolAnswerCodec {

  /**
   * Stores a message's tool answer.
   *
   * @param message the message
   * @return the stored form, or empty when the message carries no tool answer
   */
  Optional<String> encode(EngineMessage message);

  /**
   * Reads a stored tool answer back; a row written before answers were structured is read as the
   * one answer.
   *
   * @param stored the stored text
   * @return the tool message
   */
  EngineMessage decode(String stored);
}
