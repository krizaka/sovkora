package com.krizaka.sovkora.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RatchetTest {

  @Test
  void anIncreaseIsReported() {
    assertThat(Ratchet.increase(Set.of("A", "B"), Set.of("A", "B", "C"))).containsExactly("C");
  }

  @Test
  void aSwapIsAnIncrease() {
    assertThat(Ratchet.increase(Set.of("A", "B"), Set.of("A", "C"))).containsExactly("C");
  }

  @Test
  void aDecreaseIsNeverReported() {
    assertThat(Ratchet.increase(Set.of("A", "B"), Set.of("A"))).isEmpty();
  }

  @Test
  void anUpdateLowersTheBaseline() {
    var next = Ratchet.lowered(Map.of("r", Set.of("A", "B")), Map.of("r", Set.of("A")));
    assertThat(next.get("r")).containsExactly("A");
  }

  @Test
  void anUpdateNeverRaisesAKnownRule() {
    assertThatThrownBy(
            () -> Ratchet.lowered(Map.of("r", Set.of("A")), Map.of("r", Set.of("A", "C"))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anUpdateRecordsANewRuleAsItIs() {
    var next = Ratchet.lowered(Map.of(), Map.of("new", Set.of("A", "B")));
    assertThat(next.get("new")).containsExactly("A", "B");
  }
}
