package com.krizaka.sovkora.interceptor.cache;

import java.util.Objects;

/**
 * What a cached answer belongs to. Two turns share an answer only inside one partition: never
 * across tenants, never across a different scope guard (a pack change is a new partition, which is
 * also how stale answers stop being served), never across a model tier.
 *
 * @param tenantId the tenant or studio; never blank (no tenant, no cache)
 * @param scopeGuardHash a hash of the scope-guard declaration the answer was produced under
 * @param modelTier the declared tier of model that answered
 */
public record CachePartition(String tenantId, String scopeGuardHash, String modelTier) {

  /** Compact constructor: every part is required. */
  public CachePartition {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("a cache partition needs a tenant");
    }
    Objects.requireNonNull(scopeGuardHash, "scopeGuardHash");
    Objects.requireNonNull(modelTier, "modelTier");
  }
}
