package com.krizaka.sovkora.job.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PodKeyTest {

  @Test
  @DisplayName("a pod key is its UTC start and six hex digits; its subject is gpu-pod:<key>")
  void shape() {
    PodKey key = new PodKey("20261114180000-a1b2c3");
    assertThat(key.subject()).isEqualTo("gpu-pod:20261114180000-a1b2c3");
    assertThat(PodKey.ofSubject(key.subject())).isEqualTo(key);
  }

  @Test
  @DisplayName("anything else is refused: a broker user name, a service name, an injection")
  void refusesOtherShapes() {
    for (String bad :
        new String[] {
          "gpu-worker",
          "20261114180000-A1B2C3",
          "20261114180000-a1b2c",
          "x';--",
          "",
          "20261114180000-a1b2c3/../x"
        }) {
      assertThatThrownBy(() -> new PodKey(bad)).isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> PodKey.ofSubject("sovkora-studio-service"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
