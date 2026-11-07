package com.krizaka.sovkora.knowledgeservice.recommendation.domain.model;

import java.util.Objects;

/**
 * What a person's interactions say they like: the decayed, weighted mean of the vectors of the
 * items they engaged (unit length), and how many interactions made it.
 *
 * @param vector the profile vector
 * @param interactions the number of interactions it was made from
 */
public record UserProfile(Vector vector, int interactions) {

  public UserProfile {
    Objects.requireNonNull(vector, "vector");
  }
}
