package com.krizaka.sovkora.core.infrastructure.host;

import com.krizaka.sovkora.core.domain.model.HostCapability;
import java.util.Locale;
import java.util.Set;

/**
 * Detects the host's inference accelerators once at startup so the model catalog can flag models
 * whose {@code supported_hardware} the host cannot satisfy.
 *
 * <p>Apple Silicon Macs expose the Metal / CoreML / MLX stack; an NVIDIA host exposes CUDA (opt-in
 * via {@code ORAZAKA_GPU=cuda}, since CUDA cannot be probed portably from the JVM); everything else
 * falls back to CPU-only.
 */
public class HostCapabilityDetector {

  private final HostCapability capability;

  public HostCapabilityDetector() {
    // os.name/os.arch are enough to detect Apple Silicon (the current local-phase target). A CUDA
    // host override is plumbed through typed config (not raw env, per GOV-003) when remote GPU
    // hosts are introduced; detect(...) already accepts it so the wiring is a one-line change.
    this.capability =
        detect(System.getProperty("os.name", ""), System.getProperty("os.arch", ""), null);
  }

  /** The detected host capability (resolved once at construction). */
  public HostCapability capability() {
    return capability;
  }

  /**
   * Pure detection logic, extracted so it can be unit-tested without depending on the actual host.
   *
   * @param osName {@code os.name} system property.
   * @param osArch {@code os.arch} system property.
   * @param gpuOverride optional {@code ORAZAKA_GPU} env override (e.g. {@code cuda}).
   * @return the resolved {@link HostCapability}.
   */
  static HostCapability detect(String osName, String osArch, String gpuOverride) {
    String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
    String arch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
    String gpu = gpuOverride == null ? "" : gpuOverride.trim().toLowerCase(Locale.ROOT);

    if (gpu.contains("cuda")) {
      return new HostCapability(Set.of("cuda"));
    }
    boolean appleSilicon = name.contains("mac") && ("aarch64".equals(arch) || arch.contains("arm"));
    if (appleSilicon) {
      return new HostCapability(Set.of("APPLE_SILICON_MLX", "mps", "coreml"));
    }
    if (!gpu.isBlank()) {
      return new HostCapability(Set.of(gpu));
    }
    return new HostCapability(Set.of("cpu"));
  }
}
