package com.krizaka.sovkora.interceptor.cache;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where answers are kept. Every read and write is scoped to a {@link CachePartition}: an
 * implementation must never return an entry of another partition, and never one past its expiry.
 */
public interface SemanticCacheStore {

  /** A stored answer close to a query. */
  record Candidate(String response, String signature, double similarity) {}

  /** The answer stored under exactly this key in this partition, if it has not expired. */
  Optional<String> findExact(CachePartition partition, String exactKey, Instant now);

  /** The {@code k} nearest unexpired entries of this partition, best first. */
  List<Candidate> nearest(CachePartition partition, float[] embedding, int k, Instant now);

  /** Keeps an answer until {@code expiresAt}. */
  void put(
      CachePartition partition,
      String exactKey,
      float[] embedding,
      String signature,
      String response,
      Instant expiresAt);

  /** Forgets everything of a tenant; returns how many entries went. */
  int invalidateTenant(String tenantId);

  /** Forgets what has expired; returns how many entries went. */
  int purgeExpired(Instant now);
}
