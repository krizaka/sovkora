package com.krizaka.sovkora.core.infrastructure.support;

import java.io.Serial;

/**
 * Exception thrown when the AI execution pipeline fails to process a request or resolve schemas.
 */
public class PipelineExecutionException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs a new exception with the specified message and cause.
   *
   * @param message Detailed message.
   * @param cause Underlying exception trigger.
   */
  public PipelineExecutionException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new exception with the specified message.
   *
   * @param message Detailed message.
   */
  public PipelineExecutionException(String message) {
    super(message);
  }
}
