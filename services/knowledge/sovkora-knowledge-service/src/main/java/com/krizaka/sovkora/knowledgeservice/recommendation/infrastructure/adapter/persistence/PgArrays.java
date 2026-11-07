package com.krizaka.sovkora.knowledgeservice.recommendation.infrastructure.adapter.persistence;

import java.util.Collection;

/**
 * PostgreSQL array literals for {@code ?::text[]} parameters — a pure function (ERR-127 (b)). Every
 * element is double-quoted with {@code "} and {@code \} escaped, so the literal is a value, never
 * SQL, whatever the element holds.
 */
public final class PgArrays {

  private PgArrays() {}

  public static String text(Collection<String> values) {
    StringBuilder out = new StringBuilder("{");
    boolean first = true;
    for (String v : values) {
      if (!first) {
        out.append(',');
      }
      first = false;
      out.append('"').append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
    }
    return out.append('}').toString();
  }
}
