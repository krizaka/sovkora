package com.krizaka.sovkora.interceptor.validation;

/** Extracts the JSON payload of an LLM answer that may be wrapped in a markdown code fence. */
final class JsonBlocks {

  private JsonBlocks() {}

  /**
   * Extracts a JSON block from potentially markdown-fenced text.
   *
   * @param text The response text.
   * @param allowArray Whether a bare top-level JSON array counts as direct JSON.
   * @return The extracted JSON string, or null if none found.
   */
  static String extract(String text, boolean allowArray) {
    if (text == null) {
      return null;
    }
    String stripped = text.strip();
    if (stripped.startsWith("{") || (allowArray && stripped.startsWith("["))) {
      return stripped;
    }

    int start = stripped.indexOf("```json");
    if (start == -1) {
      start = stripped.indexOf("```");
    }
    if (start == -1) {
      return null;
    }

    int contentStart = stripped.indexOf('\n', start);
    if (contentStart == -1) {
      return null;
    }

    int end = stripped.indexOf("```", contentStart);
    if (end == -1) {
      return null;
    }

    return stripped.substring(contentStart + 1, end).strip();
  }
}
