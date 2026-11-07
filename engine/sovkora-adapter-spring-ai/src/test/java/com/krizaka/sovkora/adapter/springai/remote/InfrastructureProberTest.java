package com.krizaka.sovkora.adapter.springai.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InfrastructureProberTest {

  @Mock private CoreProperties coreProperties;
  @Mock private CoreProperties.VideoConfig videoConfig;
  @Mock private CoreProperties.VideoGenerationConfig videoGenerationConfig;
  @Mock private CoreProperties.ImageConfig imageConfig;
  @Mock private CoreProperties.ImageGenerationConfig imageGenerationConfig;

  @Test
  void shouldRetrieveProbePortsFromConstructor() {
    InfrastructureProber prober = new InfrastructureProber(null, 8188, 8085);
    assertThat(prober.getVideoProbePort()).isEqualTo(8188);
    assertThat(prober.getImageProbePort()).isEqualTo(8085);
    assertThat(prober.isVideoEngineOnline()).isFalse();
    assertThat(prober.isImageEngineOnline()).isFalse();
  }

  @Test
  void shouldProbeVideoInfrastructureWithUnreachablePort() {
    when(videoGenerationConfig.baseUrl()).thenReturn("http://localhost:9999");
    when(videoConfig.generation()).thenReturn(videoGenerationConfig);
    when(coreProperties.video()).thenReturn(videoConfig);

    InfrastructureProber prober = new InfrastructureProber(coreProperties, 9999, 8888);
    prober.probeVideoInfrastructure();

    assertThat(prober.isVideoEngineOnline()).isFalse();
  }

  @Test
  void shouldProbeImageInfrastructureWithUnreachablePort() {
    when(imageGenerationConfig.baseUrl()).thenReturn("http://localhost:8888");
    when(imageConfig.generation()).thenReturn(imageGenerationConfig);
    when(coreProperties.image()).thenReturn(imageConfig);

    InfrastructureProber prober = new InfrastructureProber(coreProperties, 9999, 8888);
    prober.probeImageInfrastructure();

    assertThat(prober.isImageEngineOnline()).isFalse();
  }

  @Test
  void shouldProbeWithNullBaseUrlFallback() {
    when(coreProperties.video()).thenReturn(null);
    when(coreProperties.image()).thenReturn(null);

    InfrastructureProber prober = new InfrastructureProber(coreProperties, 9999, 8888);
    prober.probeVideoInfrastructure();
    prober.probeImageInfrastructure();

    assertThat(prober.isVideoEngineOnline()).isFalse();
    assertThat(prober.isImageEngineOnline()).isFalse();
  }
}
