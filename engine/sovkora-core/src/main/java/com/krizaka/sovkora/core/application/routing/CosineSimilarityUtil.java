package com.krizaka.sovkora.core.application.routing;

/**
 * Cosine similarity utility — computes similarity between float[] embedding vectors. Used by {@link
 * DeterministicRouterEngine} for provider scoring.
 */
final class CosineSimilarityUtil {

  private CosineSimilarityUtil() {}

  /**
   * Compute cosine similarity between two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return cosine similarity in [-1.0, 1.0]
   * @throws IllegalArgumentException if vectors are null, empty, or different lengths
   */
  static double cosineSimilarity(float[] a, float[] b) {
    if (a == null || b == null) {
      throw new IllegalArgumentException("Vectors must not be null");
    }
    if (a.length != b.length) {
      throw new IllegalArgumentException(
          "Vector dimensions must match: " + a.length + " vs " + b.length);
    }
    if (a.length == 0) {
      throw new IllegalArgumentException("Vectors must not be empty");
    }

    double dotProduct = 0.0;
    double magnitudeA = 0.0;
    double magnitudeB = 0.0;

    for (int i = 0; i < a.length; i++) {
      dotProduct += (double) a[i] * b[i];
      magnitudeA += (double) a[i] * a[i];
      magnitudeB += (double) b[i] * b[i];
    }

    double denominator = Math.sqrt(magnitudeA) * Math.sqrt(magnitudeB);
    if (denominator == 0.0) {
      return 0.0;
    }
    return dotProduct / denominator;
  }
}
