package com.krizaka.sovkora.knowledge.recommendation.domain.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * An embedding: a fixed-length array of floats with the arithmetic the recommender needs (cosine,
 * weighted sums, unit length) and its pgvector text form. Immutable.
 */
public final class Vector {

  /**
   * The dimension of the recommender's vectors — the platform's {@code embedding} model (GTE Large
   * v1.5 / BGE-M3, 1024). A technical fact of the model and of the column type {@code
   * vector(1024)}: a model of another size is a migration, not a setting.
   */
  public static final int DIMENSIONS = 1024;

  private final float[] values;

  private Vector(float[] values) {
    this.values = values;
  }

  /** A vector of {@link #DIMENSIONS} finite values. */
  public static Vector of(float[] values) {
    Objects.requireNonNull(values, "values");
    if (values.length != DIMENSIONS) {
      throw new IllegalArgumentException(
          "a vector has " + DIMENSIONS + " dimensions, not " + values.length);
    }
    for (float v : values) {
      if (!Float.isFinite(v)) {
        throw new IllegalArgumentException("a vector holds finite values only");
      }
    }
    return new Vector(values.clone());
  }

  /** Parses pgvector's text form {@code [0.1,0.2,…]}. */
  public static Vector parse(String text) {
    Objects.requireNonNull(text, "text");
    String body = text.trim();
    if (body.length() < 2 || body.charAt(0) != '[' || body.charAt(body.length() - 1) != ']') {
      throw new IllegalArgumentException("not a pgvector literal");
    }
    String[] parts = body.substring(1, body.length() - 1).split(",");
    float[] values = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
      values[i] = Float.parseFloat(parts[i].trim());
    }
    return of(values);
  }

  /** pgvector's text form, for a {@code ?::vector} parameter. */
  public String literal() {
    StringBuilder out = new StringBuilder(values.length * 10).append('[');
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        out.append(',');
      }
      out.append(values[i]);
    }
    return out.append(']').toString();
  }

  /** Cosine similarity in [-1, 1]; 0 when either vector is null. */
  public double cosine(Vector other) {
    double dot = 0;
    double a = 0;
    double b = 0;
    for (int i = 0; i < values.length; i++) {
      dot += (double) values[i] * other.values[i];
      a += (double) values[i] * values[i];
      b += (double) other.values[i] * other.values[i];
    }
    if (a == 0 || b == 0) {
      return 0;
    }
    return dot / (Math.sqrt(a) * Math.sqrt(b));
  }

  /** {@code this + weight × other}, as a new vector. */
  public Vector plus(Vector other, double weight) {
    float[] sum = values.clone();
    for (int i = 0; i < sum.length; i++) {
      sum[i] += (float) (weight * other.values[i]);
    }
    return new Vector(sum);
  }

  /** A zero vector. */
  public static Vector zero() {
    return new Vector(new float[DIMENSIONS]);
  }

  /** The same direction at unit length; empty when the vector is zero. */
  public java.util.Optional<Vector> unit() {
    double norm = 0;
    for (float v : values) {
      norm += (double) v * v;
    }
    if (norm == 0) {
      return java.util.Optional.empty();
    }
    double scale = 1 / Math.sqrt(norm);
    float[] out = new float[values.length];
    for (int i = 0; i < out.length; i++) {
      out[i] = (float) (values[i] * scale);
    }
    return java.util.Optional.of(new Vector(out));
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Vector other && Arrays.equals(values, other.values);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(values);
  }

  @Override
  public String toString() {
    return "Vector[" + values.length + "]";
  }
}
