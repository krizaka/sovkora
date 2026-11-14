package com.krizaka.sovkora.job.domain.model;

import java.util.regex.Pattern;

/**
 * The key a RunPod pod is known by (krizaka-internal#3614): its UTC start and six random hex
 * digits, {@code yyyymmddHHMMSS-xxxxxx}. The pod's name ends with it, its broker user is {@code
 * gpu-pod-<key>} and its job service token's subject is {@code gpu-pod:<key>}.
 *
 * @param value the key, exactly in that shape
 */
public record PodKey(String value) {

  /** The prefix of a pod token's {@code sub} claim. */
  public static final String SUBJECT_PREFIX = "gpu-pod:";

  private static final Pattern SHAPE = Pattern.compile("[0-9]{14}-[0-9a-f]{6}");

  public PodKey {
    if (!SHAPE.matcher(value).matches()) {
      throw new IllegalArgumentException("a pod key is yyyymmddHHMMSS-xxxxxx (6 hex digits)");
    }
  }

  /**
   * The key named by a pod token's subject.
   *
   * @param subject the {@code sub} claim, {@code gpu-pod:<key>}
   * @return the key
   * @throws IllegalArgumentException when the subject is not a pod's
   */
  public static PodKey ofSubject(String subject) {
    if (!subject.startsWith(SUBJECT_PREFIX)) {
      throw new IllegalArgumentException("not a pod subject");
    }
    return new PodKey(subject.substring(SUBJECT_PREFIX.length()));
  }

  /**
   * @return the {@code sub} claim of this pod's token
   */
  public String subject() {
    return SUBJECT_PREFIX + value;
  }
}
