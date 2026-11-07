package com.krizaka.sovkora.tools.infrastructure.sandbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Jimfs sandbox environment.
 *
 * @param maxBytesPerJob Maximum bytes a single jobId sandbox can allocate (default: 256 MB).
 * @param evictionTtlSeconds TTL in seconds before orphaned sandboxes are pruned (default: 300).
 */
@ConfigurationProperties(prefix = "orazaka.tools.sandbox")
public record SandboxProperties(long maxBytesPerJob, int evictionTtlSeconds) {

  private static final long DEFAULT_MAX_BYTES = 256L * 1024 * 1024; // 256 MB
  private static final int DEFAULT_EVICTION_TTL = 300; // 5 minutes

  public SandboxProperties {
    if (maxBytesPerJob <= 0) {
      maxBytesPerJob = DEFAULT_MAX_BYTES;
    }
    if (evictionTtlSeconds <= 0) {
      evictionTtlSeconds = DEFAULT_EVICTION_TTL;
    }
  }
}
