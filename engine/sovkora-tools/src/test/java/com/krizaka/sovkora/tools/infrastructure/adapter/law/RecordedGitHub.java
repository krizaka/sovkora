package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/** The recorded answers of the GitHub API the promotion tests replay; nothing reaches GitHub. */
final class RecordedGitHub {

  static final String API = "https://api.github.com";
  static final String REPO = API + "/repos/krizaka/sovkora-packs";

  private RecordedGitHub() {}

  static String answer(String name) {
    try (InputStream in = new ClassPathResource("github/promotion/" + name).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
