package com.krizaka.sovkora.persistence.architecture;

import com.krizaka.sovkora.test.architecture.MeteringMarkerRules;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Messaging-seam guardrail [BILL-001]: a producer of {@code job.*} says whether it meters its own
 * work, so no inference is charged twice.
 *
 * <p>Hosted here because this module owns {@code MessagingContract} — the module that defines the
 * jobs exchange is the one that polices its producers — and because the check spans four services
 * that no single runtime loads together, exactly like {@code SqlBoundaryTest} above it.
 */
class MeteringMarkerTest {

  @Test
  @DisplayName("[BILL-001] every job.* producer stamps the deferred-metering marker or is exempt")
  void everyJobProducerDeclaresItsMetering() {
    // The failure this catches is silent by construction: a producer that forgets the marker
    // does not error, it takes a second hold on work its orchestration already settles.
    // ADR-044
    // measured that as sixteen credits for eight inferences, unseen for as long as billing
    // was off.
    Path root = MeteringMarkerRules.locateRepositoryRoot(Path.of(System.getProperty("user.dir")));
    MeteringMarkerRules.assertEveryJobProducerDeclaresItsMetering(root);
  }
}
