package com.krizaka.sovkora.interceptor.governance;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.krizaka.sovkora.interceptor.governance.persistence.InterceptorPolicyStore;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PolicyConfig cache — Caffeine-backed snapshot of the {@code interceptor_policy} table.
 *
 * <p>TTL: 30 seconds. Fallback: If PostgreSQL is unreachable, the last healthy memory snapshot is
 * returned without service disruption.
 */
final class PolicyConfigCache {

  private static final Logger log = LoggerFactory.getLogger(PolicyConfigCache.class);
  private static final String CACHE_KEY = "ALL_POLICIES";

  private final LoadingCache<String, List<InterceptorPolicy>> cache;
  private final AtomicReference<List<InterceptorPolicy>> lastHealthySnapshot =
      new AtomicReference<>(List.of());

  PolicyConfigCache(InterceptorPolicyStore store) {
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .build(key -> store.findAllOrdered());
  }

  List<InterceptorPolicy> getPolicies() {
    try {
      List<InterceptorPolicy> policies = cache.get(CACHE_KEY);
      if (policies != null && !policies.isEmpty()) {
        lastHealthySnapshot.set(policies);
        return policies;
      }
    } catch (Exception e) {
      log.warn("PolicyConfig cache miss — falling back to last healthy snapshot", e);
    }
    return lastHealthySnapshot.get();
  }

  void invalidate() {
    cache.invalidateAll();
  }
}
