package com.krizaka.sovkora.knowledge.infrastructure.adapter.rest.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetrieveRequestTest {

  @Test
  @DisplayName("topK defaults to 3 when null or non-positive, else passes through")
  void resolvedTopK() {
    assertThat(new RetrieveRequest("q", null).resolvedTopK()).isEqualTo(3);
    assertThat(new RetrieveRequest("q", 0).resolvedTopK()).isEqualTo(3);
    assertThat(new RetrieveRequest("q", -1).resolvedTopK()).isEqualTo(3);
    assertThat(new RetrieveRequest("q", 7).resolvedTopK()).isEqualTo(7);
  }
}
