package com.krizaka.sovkora.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class HostCapabilityTest {

  private static final HostCapability APPLE_SILICON =
      new HostCapability(Set.of("APPLE_SILICON_MLX", "mps", "coreml"));

  @Test
  void blankOrUniversalRequirement_runsAnywhere() {
    HostCapability cpuOnly = new HostCapability(Set.of("cpu"));
    assertThat(cpuOnly.supportsModelHardware(null)).isTrue();
    assertThat(cpuOnly.supportsModelHardware("")).isTrue();
    assertThat(cpuOnly.supportsModelHardware("  ")).isTrue();
    assertThat(cpuOnly.supportsModelHardware("cpu")).isTrue();
    assertThat(cpuOnly.supportsModelHardware("any")).isTrue();
  }

  @Test
  void appleSilicon_supportsMetalStack_butNotCuda() {
    assertThat(APPLE_SILICON.supportsModelHardware("mps")).isTrue();
    assertThat(APPLE_SILICON.supportsModelHardware("coreml")).isTrue();
    assertThat(APPLE_SILICON.supportsModelHardware("APPLE_SILICON_MLX")).isTrue();
    // The regression we want to catch: a cuda model cannot run on Apple Silicon.
    assertThat(APPLE_SILICON.supportsModelHardware("cuda")).isFalse();
  }

  @Test
  void requirementMatchIsCaseInsensitive() {
    assertThat(APPLE_SILICON.supportsModelHardware("MPS")).isTrue();
    assertThat(APPLE_SILICON.supportsModelHardware("apple_silicon_mlx")).isTrue();
  }

  @Test
  void describe_listsAcceleratorsSorted() {
    assertThat(APPLE_SILICON.describe()).isEqualTo("APPLE_SILICON_MLX, coreml, mps");
    assertThat(new HostCapability(Set.of()).describe()).isEqualTo("cpu");
  }

  @Test
  void acceleratorsAreDefensivelyCopiedAndNullSafe() {
    assertThat(new HostCapability(null).accelerators()).isEmpty();
  }
}
