package com.krizaka.sovkora.job.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * V5 adds the Runpod Public Endpoints provider and the two Seedance 2.5 I2V rows the media worker
 * routes (workers/media/models.yaml carries the same names and prices). Read as text: the database
 * itself is the ITs' business.
 */
class SeedRunpodSeedanceTest {

  private static final String SEED = read();

  private static String read() {
    try {
      return Files.readString(Path.of("src/main/resources/db/migration/V5__runpod_seedance.sql"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @DisplayName("the provider row carries no key: the worker reads it from its configuration")
  void theProviderCarriesNoKey() {
    assertThat(SEED)
        .contains("('runpod-public-endpoint', 'https://api.runpod.ai/v2', 'not-required')");
  }

  @Test
  @DisplayName(
      "each resolution is a video row on the provider, a run of 4 s priced from the rate card")
  void eachResolutionIsARow() {
    assertThat(SEED)
        .contains(
            "('seedance-2-5-i2v-480p', 'Seedance 2.5 I2V 480p (Runpod)', 'video', NULL, 'runpod-public-endpoint', FALSE")
        .contains(
            "('seedance-2-5-i2v-1080p', 'Seedance 2.5 I2V 1080p (Runpod)', 'video', NULL, 'runpod-public-endpoint', FALSE");
    assertThat(SEED)
        .containsPattern(
            "runs_on = 'provider'[^;]*price_cents = 72[^;]*WHERE model_name = 'seedance-2-5-i2v-480p';");
    assertThat(SEED)
        .containsPattern(
            "runs_on = 'provider'[^;]*price_cents = 360[^;]*WHERE model_name = 'seedance-2-5-i2v-1080p';");
  }
}
