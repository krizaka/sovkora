package com.krizaka.sovkora.knowledge.recommendation.domain.model;

/** The platform's embedding model could not embed the items (down, refused, wrong shape). */
public class EmbeddingUnavailableException extends RuntimeException {

  public EmbeddingUnavailableException(String message) {
    super(message);
  }

  public EmbeddingUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
