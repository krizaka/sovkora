package com.krizaka.sovkora.core.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PipelineDisabledExceptionTest {

  @Test
  @DisplayName(
      "[ADR-062] names the switch, and is not a gate's refusal — so it is not billed as one")
  void namesTheSwitchAndIsNotAShortCircuit() {
    PipelineDisabledException refused = new PipelineDisabledException();

    assertThat(refused.getMessage()).contains("orazaka.core.orchestration.enabled");
    assertThat(refused).isNotInstanceOf(PipelineShortCircuitException.class);
  }
}
