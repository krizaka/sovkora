package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.core.domain.model.checks.CheckProviderRow;
import com.krizaka.sovkora.core.domain.model.checks.CheckPurpose;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckProviderCatalog;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The provider rows of the external checks, read from the admin settings (sovkora-engine#73):
 * {@code checks.providers} lists the provider names, and for each name {@code
 * checks.provider.<name>.*} holds {@code handler-key}, {@code purposes}, {@code regions}, {@code
 * priority}, {@code enabled}, {@code endpoint} and {@code price-micros}. The routing is these rows
 * and the {@code handler-key}, never code order. A malformed row is dropped with a warning that
 * names it (never a value), so a typo switches a provider off rather than on.
 */
@Service
class SettingsCheckProviderCatalogAdapter implements CheckProviderCatalog {

  private static final Logger log =
      LoggerFactory.getLogger(SettingsCheckProviderCatalogAdapter.class);

  /** The setting that lists the provider names, comma separated. */
  static final String LIST_KEY = "checks.providers";

  private static final String PREFIX = "checks.provider.";

  private final RuntimeConfigProvider settings;

  SettingsCheckProviderCatalogAdapter(RuntimeConfigProvider settings) {
    this.settings = Objects.requireNonNull(settings, "RuntimeConfigProvider cannot be null");
  }

  @Override
  public List<CheckProviderRow> rows() {
    List<CheckProviderRow> rows = new ArrayList<>();
    for (String name : split(settings.getString(LIST_KEY, ""))) {
      try {
        rows.add(row(name.toLowerCase(Locale.ROOT)));
      } catch (RuntimeException malformed) {
        log.warn("check provider '{}' has a malformed row and is ignored", name);
      }
    }
    return List.copyOf(rows);
  }

  private CheckProviderRow row(String name) {
    String base = PREFIX + name + ".";
    Set<CheckPurpose> purposes = EnumSet.noneOf(CheckPurpose.class);
    for (String purpose : split(settings.getString(base + "purposes", ""))) {
      purposes.add(CheckPurpose.valueOf(purpose.toUpperCase(Locale.ROOT)));
    }
    String endpoint = settings.getString(base + "endpoint", null);
    return new CheckProviderRow(
        name,
        settings.getString(base + "handler-key", ""),
        purposes,
        split(settings.getString(base + "regions", "")).stream()
            .map(region -> region.toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet()),
        settings.getInt(base + "priority", 100),
        settings.getBoolean(base + "enabled", false),
        endpoint == null || endpoint.isBlank() ? null : endpoint.strip(),
        Long.parseLong(settings.getString(base + "price-micros", "0").strip()));
  }

  private static List<String> split(String csv) {
    return Arrays.stream(csv.split(","))
        .map(String::strip)
        .filter(value -> !value.isEmpty())
        .toList();
  }
}
