package com.krizaka.sovkora.tools.infrastructure.law;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Adds to Hibernate's generated schema what the job-service law tables have beyond it. */
final class LawTestSchema {

  private LawTestSchema() {}

  static void apply(JdbcTemplate jdbc) {
    try (InputStream in = new ClassPathResource("law-corpus-constraints.sql").getInputStream()) {
      jdbc.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
