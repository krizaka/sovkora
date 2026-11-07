package com.krizaka.sovkora.core.domain.model;

import java.util.Locale;
import java.util.Set;

/**
 * The hardware accelerators available on the host that runs inference — e.g. {@code mps}, {@code
 * coreml} and {@code APPLE_SILICON_MLX} on an Apple Silicon Mac, or {@code cuda} on an NVIDIA box.
 *
 * <p>Used to gate {@code orazaka_models.supported_hardware}: a model requiring hardware the host
 * lacks (e.g. a {@code cuda} model on Apple Silicon) is flagged incompatible up front instead of
 * failing at generation time with an opaque worker error.
 */
public record HostCapability(Set<String> accelerators) {

  public HostCapability {
    accelerators = accelerators == null ? Set.of() : Set.copyOf(accelerators);
  }

  /**
   * Whether a model declaring {@code supportedHardware} can run on this host. A blank value — or
   * the universal markers {@code cpu}/{@code any}/{@code universal} — runs anywhere; otherwise the
   * requirement must match one of the host's accelerators (case-insensitively).
   *
   * @param supportedHardware the model's {@code supported_hardware} catalog value (nullable).
   * @return {@code true} when the host can run a model with that hardware requirement.
   */
  public boolean supportsModelHardware(String supportedHardware) {
    if (supportedHardware == null || supportedHardware.isBlank()) {
      return true;
    }
    String required = supportedHardware.trim().toLowerCase(Locale.ROOT);
    if ("cpu".equals(required) || "any".equals(required) || "universal".equals(required)) {
      return true;
    }
    return accelerators.stream().anyMatch(accelerator -> accelerator.equalsIgnoreCase(required));
  }

  /** Sorted, comma-separated accelerator list for diagnostics (e.g. CLI report, lock reasons). */
  public String describe() {
    return accelerators.isEmpty()
        ? "cpu"
        : String.join(", ", accelerators.stream().sorted().toList());
  }
}
