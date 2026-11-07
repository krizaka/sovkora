package com.krizaka.sovkora.persistence.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.checks.CheckProviderRow;
import com.krizaka.sovkora.core.domain.model.checks.CheckPurpose;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the provider rows are the admin's settings, a typo switching a provider off")
class SettingsCheckProviderCatalogAdapterTest {

  private final Map<String, String> rows = new HashMap<>();
  private final RuntimeConfigProvider settings =
      new RuntimeConfigProvider() {
        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
          return rows.containsKey(key) ? Boolean.parseBoolean(rows.get(key)) : defaultValue;
        }

        @Override
        public int getInt(String key, int defaultValue) {
          return rows.containsKey(key) ? Integer.parseInt(rows.get(key)) : defaultValue;
        }

        @Override
        public String getString(String key, String defaultValue) {
          return rows.getOrDefault(key, defaultValue);
        }
      };

  @Test
  @DisplayName("a provider's row reads handler key, purposes, regions, priority, endpoint, price")
  void readsARow() {
    rows.put("checks.providers", "soquij");
    rows.put("checks.provider.soquij.handler-key", "soquij");
    rows.put("checks.provider.soquij.purposes", "court_records");
    rows.put("checks.provider.soquij.regions", "qc");
    rows.put("checks.provider.soquij.priority", "1");
    rows.put("checks.provider.soquij.enabled", "true");
    rows.put("checks.provider.soquij.endpoint", "https://soquij.example/");
    rows.put("checks.provider.soquij.price-micros", "250000");

    CheckProviderRow row = new SettingsCheckProviderCatalogAdapter(settings).rows().getFirst();

    assertThat(row.provider()).isEqualTo("soquij");
    assertThat(row.handlerKey()).isEqualTo("soquij");
    assertThat(row.purposes()).isEqualTo(Set.of(CheckPurpose.COURT_RECORDS));
    assertThat(row.regions()).isEqualTo(Set.of("QC"));
    assertThat(row.priority()).isEqualTo(1);
    assertThat(row.enabled()).isTrue();
    assertThat(row.endpoint()).isEqualTo("https://soquij.example/");
    assertThat(row.priceMicros()).isEqualTo(250_000L);
  }

  @Test
  @DisplayName("a row not switched on is disabled, and a malformed one is dropped")
  void disabledByDefaultAndMalformedDropped() {
    rows.put("checks.providers", "trustii, broken, certn");
    rows.put("checks.provider.trustii.handler-key", "trustii");
    rows.put("checks.provider.trustii.purposes", "credit");
    rows.put("checks.provider.broken.handler-key", "certn");
    rows.put("checks.provider.broken.purposes", "weather");
    rows.put("checks.provider.certn.handler-key", "certn");
    rows.put("checks.provider.certn.purposes", "court_records,identity");
    rows.put("checks.provider.certn.price-micros", "not a number");

    var read = new SettingsCheckProviderCatalogAdapter(settings).rows();

    assertThat(read).extracting(CheckProviderRow::provider).containsExactly("trustii");
    assertThat(read.getFirst().enabled()).isFalse();
    assertThat(read.getFirst().endpoint()).isNull();
  }

  @Test
  @DisplayName("no list, no provider")
  void emptyList() {
    assertThat(new SettingsCheckProviderCatalogAdapter(settings).rows()).isEmpty();
  }
}
