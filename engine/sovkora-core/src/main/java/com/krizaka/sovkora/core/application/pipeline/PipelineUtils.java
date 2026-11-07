package com.krizaka.sovkora.core.application.pipeline;

/** Shared utility methods for pipeline-related string operations. */
final class PipelineUtils {

  private PipelineUtils() {}

  /**
   * Converts a PascalCase class name into a human-readable label.
   *
   * @param className The simple class name.
   * @return A space-separated human-readable string.
   */
  static String humanize(String className) {
    StringBuilder sb = new StringBuilder(className.length() + 8);
    for (int i = 0; i < className.length(); i++) {
      char c = className.charAt(i);
      if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(className.charAt(i - 1))) {
        sb.append(' ');
      }
      sb.append(c);
    }
    return sb.toString();
  }
}
