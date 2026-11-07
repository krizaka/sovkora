package com.krizaka.sovkora.persistence.architecture;

import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.sovkora.test.architecture.GovernanceSubjects;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

/**
 * The guard every governance rule must carry [GOV-006], run where the repository-wide rules run.
 *
 * <p>Here rather than in {@code sovkora-test-support}, which declares no test engine: [PACK-001]
 * and [BILL-001] already run from this module for the same reason.
 */
class GovernanceSubjectsTest {

  @Test
  @DisplayName(
      "[GOV-006] an empty population fails the rule, naming the rule and what it found none of")
  void anEmptyPopulationFailsNamingTheRule() {
    AssertionFailedError list =
        assertThrows(
            AssertionFailedError.class,
            () -> GovernanceSubjects.require("PACK-001", "pack rows", List.of()));
    AssertionFailedError map =
        assertThrows(
            AssertionFailedError.class,
            () -> GovernanceSubjects.require("X-1", "entries", Map.of()));

    assertTrue(list.getMessage().contains("[PACK-001] examined no pack rows"), list.getMessage());
    assertTrue(map.getMessage().contains("[X-1] examined no entries"), map.getMessage());
  }

  @Test
  @DisplayName("[GOV-006] a non-empty population is returned unchanged")
  void aNonEmptyPopulationPassesThrough() {
    List<String> packs = List.of("wellbeing");

    assertSame(packs, GovernanceSubjects.require("PACK-001", "pack rows", packs));
  }

  @Test
  @DisplayName("[GOV-006] every governance rule is non-vacuous by construction")
  void everyRuleIsNonVacuous() {
    assertDoesNotThrow(GovernanceSubjects::assertEveryRuleIsNonVacuous);
  }
}
