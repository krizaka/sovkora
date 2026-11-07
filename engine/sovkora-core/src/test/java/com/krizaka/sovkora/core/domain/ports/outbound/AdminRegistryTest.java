package com.krizaka.sovkora.core.domain.ports.outbound;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AdminRegistry} — thread-safe lock/unlock/query lifecycle. */
class AdminRegistryTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  private AdminRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new AdminRegistry();
  }

  @Nested
  @DisplayName("Lock lifecycle")
  class LockLifecycle {

    @Test
    @DisplayName("lock stores LockInfo retrievable via getLock")
    void lockAndGet() {
      registry.lock("orazaka.core.chat.image", "Maintenance window");
      var lock = registry.getLock("orazaka.core.chat.image");
      assertTrue(lock.isPresent());
      assertEquals("Maintenance window", lock.get().reason());
      assertNotNull(lock.get().lockedAt());
    }

    @Test
    @DisplayName("unlock removes lock")
    void unlockRemoves() {
      registry.lock("orazaka.core.chat.image", "Maintenance");
      registry.unlock("orazaka.core.chat.image");
      assertTrue(registry.getLock("orazaka.core.chat.image").isEmpty());
    }

    @Test
    @DisplayName("getLock returns empty for unlocked feature")
    void getUnlocked() {
      assertTrue(registry.getLock("orazaka.core.chat.text").isEmpty());
    }

    @Test
    @DisplayName("unlock non-existent feature is no-op")
    void unlockNonExistent() {
      assertDoesNotThrow(() -> registry.unlock("non.existent"));
    }
  }

  @Nested
  @DisplayName("Null safety")
  class NullSafety {

    @Test
    @DisplayName("lock with null featureId throws NPE")
    void lockNullId() {
      assertThrows(NullPointerException.class, () -> registry.lock(null, "reason"));
    }

    @Test
    @DisplayName("lock with null reason throws NPE")
    void lockNullReason() {
      assertThrows(NullPointerException.class, () -> registry.lock("id", null));
    }

    @Test
    @DisplayName("unlock with null throws NPE")
    void unlockNull() {
      assertThrows(NullPointerException.class, () -> registry.unlock(null));
    }

    @Test
    @DisplayName("getLock with null throws NPE")
    void getLockNull() {
      assertThrows(NullPointerException.class, () -> registry.getLock(null));
    }
  }

  @Nested
  @DisplayName("LockInfo record")
  class LockInfoTests {

    @Test
    @DisplayName("null reason throws NPE")
    void nullReason() {
      var now = LocalDateTime.now(FIXED_CLOCK);
      assertThrows(NullPointerException.class, () -> new AdminRegistry.LockInfo(null, now));
    }

    @Test
    @DisplayName("null lockedAt throws NPE")
    void nullLockedAt() {
      assertThrows(NullPointerException.class, () -> new AdminRegistry.LockInfo("reason", null));
    }
  }
}
