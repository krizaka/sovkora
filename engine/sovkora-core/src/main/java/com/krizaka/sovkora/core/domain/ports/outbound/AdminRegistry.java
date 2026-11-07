package com.krizaka.sovkora.core.domain.ports.outbound;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe runtime store managing lock status for capabilities under Project Loom. */
public class AdminRegistry {

  private static final String FEATURE_ID_NULL_MSG = "Feature ID cannot be null";

  /**
   * Immutable audit record for a capability lock.
   *
   * @param reason Human-readable explanation for why the capability was locked.
   * @param lockedAt Timestamp when the lock was applied.
   */
  public record LockInfo(String reason, LocalDateTime lockedAt) {
    public LockInfo {
      Objects.requireNonNull(reason, "reason cannot be null");
      Objects.requireNonNull(lockedAt, "lockedAt cannot be null");
    }
  }

  private final Map<String, LockInfo> locks = new ConcurrentHashMap<>();

  /**
   * Locks a capability node with a specified reason and current timestamp.
   *
   * @param featureId The canonical capability ID to lock (e.g., {@code "orazaka.core.chat.image"}).
   * @param reason Human-readable reason for the lock.
   * @throws NullPointerException If featureId or reason is null.
   */
  public void lock(String featureId, String reason) {
    Objects.requireNonNull(featureId, FEATURE_ID_NULL_MSG);
    Objects.requireNonNull(reason, "Lock reason cannot be null");
    locks.put(featureId, new LockInfo(reason, LocalDateTime.now(ZoneOffset.UTC)));
  }

  /**
   * Unlocks a previously locked capability node.
   *
   * @param featureId The canonical capability ID to unlock.
   * @throws NullPointerException If featureId is null.
   */
  public void unlock(String featureId) {
    Objects.requireNonNull(featureId, FEATURE_ID_NULL_MSG);
    locks.remove(featureId);
  }

  /**
   * Resolves lock details for a capability node.
   *
   * @param featureId The canonical capability ID to query.
   * @return An {@link Optional} containing the {@link LockInfo} if locked, or empty if unlocked.
   * @throws NullPointerException If featureId is null.
   */
  public Optional<LockInfo> getLock(String featureId) {
    Objects.requireNonNull(featureId, FEATURE_ID_NULL_MSG);
    return Optional.ofNullable(locks.get(featureId));
  }
}
