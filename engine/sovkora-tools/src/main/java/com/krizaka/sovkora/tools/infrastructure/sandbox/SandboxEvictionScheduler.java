package com.krizaka.sovkora.tools.infrastructure.sandbox;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled eviction process — prunes expired or orphaned jobId sandbox contexts from memory.
 *
 * <p>Runs every 60 seconds. Evicts sandboxes older than the configured TTL ({@link
 * SandboxProperties#evictionTtlSeconds()}).
 */
@Component
class SandboxEvictionScheduler {

  private static final Logger log = LoggerFactory.getLogger(SandboxEvictionScheduler.class);

  private final SandboxLifecycle sandboxLifecycle;
  private final SandboxProperties properties;

  SandboxEvictionScheduler(SandboxLifecycle sandboxLifecycle, SandboxProperties properties) {
    this.sandboxLifecycle = sandboxLifecycle;
    this.properties = properties;
  }

  @Scheduled(fixedDelay = 60_000)
  void evictExpiredSandboxes() {
    Instant cutoff = Instant.now().minus(Duration.ofSeconds(properties.evictionTtlSeconds()));
    List<String> expired = new ArrayList<>();

    for (String jobId : sandboxLifecycle.activeJobIds()) {
      Instant createdAt = sandboxLifecycle.getCreatedAt(jobId);
      if (createdAt != null && createdAt.isBefore(cutoff)) {
        expired.add(jobId);
      }
    }

    for (String jobId : expired) {
      sandboxLifecycle.rollback(jobId);
      log.info("Evicted expired sandbox for jobId={}", jobId);
    }

    if (!expired.isEmpty()) {
      log.info("Sandbox eviction cycle completed: {} sandboxes pruned", expired.size());
    }
  }
}
