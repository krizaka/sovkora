package com.krizaka.sovkora.persistence.architecture;

import com.krizaka.sovkora.test.architecture.PackCoherenceRules;
import com.krizaka.sovkora.test.architecture.SqlBoundaryRules;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Data-seam guardrails over {@code infra/initdb}: what the seed files must be true of, checked
 * without a database on every build.
 *
 * <p>[SEAM-001] every FK stays inside its bounded-context file — cross-context references are
 * opaque ids only (AGENTS.md §5). [PACK-001] the Pack catalogue stays coherent across the seam that
 * rule creates: the studio context's {@code pack_studio} rows and the billing context's {@code
 * billing_pack_entitlement} rows are joined by nothing but an opaque string, so the one place their
 * agreement is checkable is here (ADR-036).
 */
class SqlBoundaryTest {

  @Test
  @DisplayName("[SEAM-001] infra/initdb has no cross-context foreign keys")
  void noCrossContextForeignKeys() {
    Path initdb = SqlBoundaryRules.locateInitDb(Path.of(System.getProperty("user.dir")));
    SqlBoundaryRules.assertNoCrossContextForeignKeys(initdb);
  }

  @Test
  @DisplayName("[PACK-001] every studio in a pack has the entitlement row that unlocks it")
  void packCatalogueIsCoherent() {
    // The failure this catches: an actor buys a pack, the subscription is written, and the
    // entitlement union comes back without the studio they paid for. Silent,
    // indistinguishable
    // from a billing bug, and aimed at the customer who just gave you money.
    Path initdb = SqlBoundaryRules.locateInitDb(Path.of(System.getProperty("user.dir")));
    PackCoherenceRules.assertPackCatalogueIsCoherent(initdb);
  }
}
