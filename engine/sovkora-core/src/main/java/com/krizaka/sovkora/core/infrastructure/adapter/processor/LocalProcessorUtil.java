package com.krizaka.sovkora.core.infrastructure.adapter.processor;

/**
 * Stateless, dependency-free helpers shared by the local audio/video processors.
 *
 * <p>Provider URL resolution (which needs the catalog) moved to the injectable {@link
 * ProviderEndpointResolver} [ERR-127]; only pure functions remain here.
 */
final class LocalProcessorUtil {

  private LocalProcessorUtil() {}

  /** Returns {@code true} if every byte in the array is zero. */
  static boolean isAllZeros(byte[] data) {
    for (byte b : data) {
      if (b != 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Resolves the transcription model: the request's, else the configured default — <b>as named</b>.
   *
   * <p>This used to rewrite every name containing "whisper" to {@code whisper-1}, so the
   * catalogue's {@code whisper-base} — the default it prices and reports — was served by whatever
   * LocalAI calls {@code whisper-1}, a tiny model that mangles French. The name the catalogue
   * declares is the model that runs (ADR-076); a host serves it under that name (infra/localai/).
   *
   * @param requestModel explicit model from the request (may be null).
   * @param defaultModel the configured default model.
   * @return the resolved model name.
   */
  static String resolveWhisperModel(String requestModel, String defaultModel) {
    return requestModel != null && !requestModel.isBlank() ? requestModel : defaultModel;
  }
}
