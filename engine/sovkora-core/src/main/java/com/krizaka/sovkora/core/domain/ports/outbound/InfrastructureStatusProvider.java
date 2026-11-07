package com.krizaka.sovkora.core.domain.ports.outbound;

/**
 * Outbound port exposing the live availability of local inference infrastructure (image/video
 * engines), as observed by a background prober. Lets the application read engine status without
 * depending on the infrastructure that performs the probing.
 */
public interface InfrastructureStatusProvider {

  /**
   * @return {@code true} if the video inference engine was responsive at the last probe.
   */
  boolean isVideoEngineOnline();

  /**
   * @return {@code true} if the image inference engine was responsive at the last probe.
   */
  boolean isImageEngineOnline();

  /**
   * @return the configured video probe port.
   */
  int getVideoProbePort();

  /**
   * @return the configured image probe port.
   */
  int getImageProbePort();
}
