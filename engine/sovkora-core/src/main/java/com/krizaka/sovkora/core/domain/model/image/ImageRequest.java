package com.krizaka.sovkora.core.domain.model.image;

import com.krizaka.sovkora.core.domain.model.AiRequest;
import com.krizaka.sovkora.core.domain.model.Context;
import java.util.List;
import java.util.Map;

/**
 * Standardized image request record for multi-modal generation.
 *
 * @param prompt Descriptive text for the image to generate.
 * @param width Desired width in pixels (optional).
 * @param height Desired height in pixels (optional).
 * @param model Desired AI model name (optional).
 * @param settings Additional provider-specific settings.
 * @param context The execution context carrying user preferences.
 * @param direction Negative prompt, seed and step count (optional; never null once built).
 * @param references Pictures the image must stay faithful to, in order (never null; at most {@value
 *     #MAX_REFERENCES}); a model that cannot take them refuses the request by name
 */
public record ImageRequest(
    String prompt,
    Integer width,
    Integer height,
    String model,
    Map<String, Object> settings,
    Context context,
    ImageDirection direction,
    List<ImageReference> references)
    implements AiRequest {

  // @fixed: the most references any image model of the catalogue conditions on in one pass.
  public static final int MAX_REFERENCES = 8;

  public ImageRequest {
    AiRequest.requireValid(prompt, context);
    settings = settings != null ? Map.copyOf(settings) : Map.of();
    direction = direction != null ? direction : ImageDirection.none();
    references = references != null ? List.copyOf(references) : List.of();
    if (references.size() > MAX_REFERENCES) {
      throw new IllegalArgumentException(
          "at most " + MAX_REFERENCES + " reference images, was " + references.size());
    }
  }

  /**
   * A request without reference images.
   *
   * @param prompt descriptive text for the image
   * @param width desired width in pixels (optional)
   * @param height desired height in pixels (optional)
   * @param model desired model name (optional)
   * @param settings provider-specific settings
   * @param context the execution context
   * @param direction negative prompt, seed and step count (optional)
   */
  public ImageRequest(
      String prompt,
      Integer width,
      Integer height,
      String model,
      Map<String, Object> settings,
      Context context,
      ImageDirection direction) {
    this(prompt, width, height, model, settings, context, direction, List.of());
  }

  /**
   * A request that leaves seed, negative prompt and steps to the model's recipe.
   *
   * @param prompt descriptive text for the image
   * @param width desired width in pixels (optional)
   * @param height desired height in pixels (optional)
   * @param model desired model name (optional)
   * @param settings provider-specific settings
   * @param context the execution context
   */
  public ImageRequest(
      String prompt,
      Integer width,
      Integer height,
      String model,
      Map<String, Object> settings,
      Context context) {
    this(prompt, width, height, model, settings, context, ImageDirection.none());
  }
}
