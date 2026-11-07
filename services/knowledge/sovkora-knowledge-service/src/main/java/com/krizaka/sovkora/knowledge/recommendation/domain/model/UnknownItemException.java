package com.krizaka.sovkora.knowledge.recommendation.domain.model;

/** The item is not in the tenant's catalogue, or has no vector yet. */
public class UnknownItemException extends RuntimeException {

  public UnknownItemException() {
    super("unknown item");
  }
}
