package com.krizaka.sovkora.core.infrastructure.provider.model;

import com.krizaka.sovkora.core.domain.model.StepModelUnavailableException;
import java.io.Serial;

/**
 * The provider bound to a model cannot be reached — for a local model (Ollama, LocalAI behind the
 * GPU relay) it means no worker is connected. Says which model and which provider, so the turn
 * fails with a reason instead of a bare "Connection refused".
 */
public class ModelUnavailableException extends StepModelUnavailableException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * @param model the model the turn asked for
   * @param provider where the catalogue binds it (provider name and base URL)
   * @param cause the connection failure
   */
  public ModelUnavailableException(String model, String provider, Throwable cause) {
    super(
        "Model '"
            + model
            + "' is served by "
            + provider
            + ", which is temporarily unavailable: no worker is connected or the provider cannot"
            + " currently serve it. Check its declared endpoint and available workers.",
        cause);
  }
}
