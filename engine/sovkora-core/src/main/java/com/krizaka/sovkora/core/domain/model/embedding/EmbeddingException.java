package com.krizaka.sovkora.core.domain.model.embedding;

import java.io.Serial;

/**
 * An embedding could not be produced: no embedding model is configured, the provider refused or did
 * not answer, or its answer was malformed. Never carries a provider key or an input text.
 */
public class EmbeddingException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public EmbeddingException(String message) {
    super(message);
  }

  public EmbeddingException(String message, Throwable cause) {
    super(message, cause);
  }
}
