package com.krizaka.sovkora.knowledgeservice.recommendation.infrastructure.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmbeddingDatumTest {

  @Test
  @DisplayName("two embeddings are equal when their vectors hold the same numbers")
  void comparesVectorContent() {
    var a = new HttpTextEmbeddingAdapter.EmbeddingAnswer.Datum(0, new float[] {1f, 2f});
    var b = new HttpTextEmbeddingAdapter.EmbeddingAnswer.Datum(0, new float[] {1f, 2f});
    var c = new HttpTextEmbeddingAdapter.EmbeddingAnswer.Datum(0, new float[] {1f, 3f});
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c);
    assertThat(a.toString()).contains("[1.0, 2.0]");
  }
}
