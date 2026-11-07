package com.krizaka.sovkora.core.infrastructure.host;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.HostCapability;
import org.junit.jupiter.api.Test;

class HostCapabilityDetectorTest {

  @Test
  void appleSiliconMac_exposesMetalStack_notCuda() {
    HostCapability host = HostCapabilityDetector.detect("Mac OS X", "aarch64", null);

    assertThat(host.supportsModelHardware("mps")).isTrue();
    assertThat(host.supportsModelHardware("APPLE_SILICON_MLX")).isTrue();
    assertThat(host.supportsModelHardware("cuda")).isFalse();
  }

  @Test
  void cudaOverride_exposesCudaOnly() {
    HostCapability host = HostCapabilityDetector.detect("Linux", "amd64", "cuda");

    assertThat(host.supportsModelHardware("cuda")).isTrue();
    assertThat(host.supportsModelHardware("mps")).isFalse();
  }

  @Test
  void nonAcceleratedHost_fallsBackToCpu() {
    HostCapability host = HostCapabilityDetector.detect("Linux", "amd64", null);

    assertThat(host.supportsModelHardware("cuda")).isFalse();
    assertThat(host.supportsModelHardware("mps")).isFalse();
    assertThat(host.supportsModelHardware(null)).isTrue(); // universal models still run
  }

  @Test
  void handlesNullSystemProperties() {
    HostCapability host = HostCapabilityDetector.detect(null, null, null);
    assertThat(host.accelerators()).containsExactly("cpu");
  }
}
