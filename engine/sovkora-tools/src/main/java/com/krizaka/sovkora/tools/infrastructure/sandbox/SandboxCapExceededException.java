package com.krizaka.sovkora.tools.infrastructure.sandbox;

/** Thrown when a sandbox's byte allocation exceeds the configured cap. */
public class SandboxCapExceededException extends RuntimeException {

  public SandboxCapExceededException(String message) {
    super(message);
  }
}
