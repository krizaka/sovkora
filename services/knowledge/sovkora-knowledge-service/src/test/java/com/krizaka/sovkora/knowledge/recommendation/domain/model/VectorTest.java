package com.krizaka.sovkora.knowledge.recommendation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VectorTest {

  @Test
  @DisplayName("a vector round-trips through pgvector's text form")
  void roundTrip() {
    Vector v = Fixtures.axis(3, 7, 0.5f);
    assertThat(Vector.parse(v.literal())).isEqualTo(v);
  }

  @Test
  @DisplayName("cosine: 1 for the same direction, 0 for orthogonal, 0 against zero")
  void cosine() {
    assertThat(Fixtures.axis(1).cosine(Fixtures.axis(1))).isEqualTo(1.0);
    assertThat(Fixtures.axis(1).cosine(Fixtures.axis(2))).isEqualTo(0.0);
    assertThat(Fixtures.axis(1).cosine(Vector.zero())).isEqualTo(0.0);
  }

  @Test
  @DisplayName("only 1024 finite values make a vector")
  void refusesWrongShape() {
    assertThatThrownBy(() -> Vector.of(new float[3])).isInstanceOf(IllegalArgumentException.class);
    float[] nan = new float[Vector.DIMENSIONS];
    nan[0] = Float.NaN;
    assertThatThrownBy(() -> Vector.of(nan)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Vector.parse("1,2")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("unit() normalises, and is empty for the zero vector")
  void unit() {
    Vector v = Vector.zero().plus(Fixtures.axis(0), 3);
    assertThat(v.unit()).contains(Fixtures.axis(0));
    assertThat(Vector.zero().unit()).isEmpty();
  }
}
