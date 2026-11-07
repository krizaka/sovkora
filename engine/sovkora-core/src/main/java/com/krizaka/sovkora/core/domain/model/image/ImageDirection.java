package com.krizaka.sovkora.core.domain.model.image;

/**
 * How an image is to be made, beyond what it shows: what to keep out of it, which seed to start
 * from, and how many denoising steps to spend.
 *
 * <p>Every field is optional, and absence means "the model's own recipe": the catalogue states a
 * model's step count ({@code orazaka_models.max_steps}) and the server it runs on states its
 * guidance and sampler at launch. A caller that names a seed gets the same image back for the same
 * prompt, size and model — which is what makes a published image reproducible, not a claim.
 *
 * <p>Self-validating (ERR-106): a value that no backend could honour is refused here, at the
 * boundary, rather than sent and silently clamped.
 *
 * @param negativePrompt what to keep out of the image; {@code null} or blank when nothing is.
 *     Honoured by models that sample with classifier-free guidance; a distilled model running at
 *     guidance 1 ignores it, and says so in its catalogue description
 * @param seed the noise seed, {@code null} for a fresh one; never negative
 * @param steps denoising steps, {@code null} for the model's own count; between 1 and {@value
 *     #MAX_STEPS}
 */
public record ImageDirection(String negativePrompt, Long seed, Integer steps) {

  /** No local model is distilled for more; anything above is a typo that would burn minutes. */
  public static final int MAX_STEPS = 150;

  /** The longest negative prompt accepted — the same bound the Studio schema puts on a prompt. */
  public static final int MAX_NEGATIVE_PROMPT = 2000;

  private static final ImageDirection NONE = new ImageDirection(null, null, null);

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public ImageDirection {
    negativePrompt =
        negativePrompt == null || negativePrompt.isBlank() ? null : negativePrompt.strip();
    if (negativePrompt != null && negativePrompt.length() > MAX_NEGATIVE_PROMPT) {
      throw new IllegalArgumentException(
          "negativePrompt must be at most " + MAX_NEGATIVE_PROMPT + " characters");
    }
    if (seed != null && seed < 0) {
      throw new IllegalArgumentException("seed must be >= 0");
    }
    if (steps != null && (steps < 1 || steps > MAX_STEPS)) {
      throw new IllegalArgumentException("steps must be between 1 and " + MAX_STEPS);
    }
  }

  /**
   * @return the direction that leaves everything to the model's recipe
   */
  public static ImageDirection none() {
    return NONE;
  }
}
