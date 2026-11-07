package com.krizaka.sovkora.knowledge.infrastructure.adapter.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RagIndexCommandTest {

  @Test
  @DisplayName("Carries the tool id and rejects a null one at the boundary")
  void validatesToolId() {
    assertThat(new RagIndexCommand("searchWeb").toolId()).isEqualTo("searchWeb");
    assertThatNullPointerException().isThrownBy(() -> new RagIndexCommand(null));
  }
}
