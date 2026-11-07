package com.krizaka.sovkora.interceptor.cache;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A bounded in-memory store: the reference implementation of the contract, for tests and a single
 * node without a database. Production keeps answers in PostgreSQL (pgvector).
 */
public final class InMemorySemanticCacheStore implements SemanticCacheStore {

  private record Entry(
      CachePartition partition,
      String exactKey,
      float[] embedding,
      String signature,
      String response,
      Instant expiresAt) {

    @Override
    public boolean equals(Object other) {
      return other instanceof Entry e
          && partition.equals(e.partition)
          && exactKey.equals(e.exactKey)
          && Arrays.equals(embedding, e.embedding)
          && signature.equals(e.signature)
          && response.equals(e.response)
          && expiresAt.equals(e.expiresAt);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          partition, exactKey, Arrays.hashCode(embedding), signature, response, expiresAt);
    }

    @Override
    public String toString() {
      return "Entry[partition="
          + partition
          + ", exactKey="
          + exactKey
          + ", embedding="
          + Arrays.toString(embedding)
          + ", signature="
          + signature
          + ", expiresAt="
          + expiresAt
          + "]";
    }
  }

  private final List<Entry> entries = new CopyOnWriteArrayList<>();
  private final int capacity;

  /** A store that keeps at most {@code capacity} entries (the oldest go first). */
  public InMemorySemanticCacheStore(int capacity) {
    this.capacity = capacity;
  }

  @Override
  public Optional<String> findExact(CachePartition partition, String exactKey, Instant now) {
    return entries.stream()
        .filter(e -> e.partition().equals(partition) && e.exactKey().equals(exactKey))
        .filter(e -> e.expiresAt().isAfter(now))
        .map(Entry::response)
        .findFirst();
  }

  @Override
  public List<Candidate> nearest(CachePartition partition, float[] embedding, int k, Instant now) {
    return entries.stream()
        .filter(e -> e.partition().equals(partition) && e.expiresAt().isAfter(now))
        .map(e -> new Candidate(e.response(), e.signature(), cosine(embedding, e.embedding())))
        .sorted(Comparator.comparingDouble(Candidate::similarity).reversed())
        .limit(k)
        .toList();
  }

  @Override
  public synchronized void put(
      CachePartition partition,
      String exactKey,
      float[] embedding,
      String signature,
      String response,
      Instant expiresAt) {
    entries.removeIf(e -> e.partition().equals(partition) && e.exactKey().equals(exactKey));
    entries.add(new Entry(partition, exactKey, embedding, signature, response, expiresAt));
    while (entries.size() > capacity) {
      entries.removeFirst();
    }
  }

  @Override
  public synchronized int invalidateTenant(String tenantId) {
    List<Entry> gone =
        new ArrayList<>(
            entries.stream().filter(e -> e.partition().tenantId().equals(tenantId)).toList());
    entries.removeAll(gone);
    return gone.size();
  }

  @Override
  public synchronized int purgeExpired(Instant now) {
    List<Entry> gone =
        new ArrayList<>(entries.stream().filter(e -> !e.expiresAt().isAfter(now)).toList());
    entries.removeAll(gone);
    return gone.size();
  }

  /** Cosine similarity of two vectors; 0 when either is empty or they differ in length. */
  static double cosine(float[] a, float[] b) {
    if (a == null || b == null || a.length == 0 || a.length != b.length) {
      return 0.0;
    }
    double dot = 0;
    double na = 0;
    double nb = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      na += a[i] * a[i];
      nb += b[i] * b[i];
    }
    return na == 0 || nb == 0 ? 0.0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
  }
}
