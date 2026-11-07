package com.krizaka.sovkora.adapter.springai.image;

import com.krizaka.sovkora.core.domain.model.MediaCategory;
import com.krizaka.sovkora.core.domain.model.image.ImageDirection;
import com.krizaka.sovkora.core.domain.model.image.ImageRequest;
import com.krizaka.sovkora.core.domain.model.image.ImageResponse;
import com.krizaka.sovkora.core.domain.model.image.ImageUsage;
import com.krizaka.sovkora.core.domain.ports.outbound.ImageGeneratorClient;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import com.krizaka.sovkora.core.infrastructure.support.CoreException;
import com.krizaka.sovkora.persistence.domain.model.CatalogModelDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adapter implementation of {@link ImageGeneratorClient} wrapping Spring AI's {@link ImageModel}.
 *
 * <p>It speaks the <b>stable-diffusion.cpp dialect</b> of the OpenAI images API — the one image
 * backend the platform runs ({@code sd-server}, Metal). The size travels in the standard {@code
 * size} field; the step count, the seed and the negative prompt travel in the {@code
 * <sd_cpp_extra_args>} block the server strips from the prompt before it samples. Guidance and
 * sampler are the model's recipe and are stated by the server at launch ({@code sovkora models
 * image}), never per request.
 */
public class SpringAiImageGeneratorClient implements ImageGeneratorClient {

  private static final Logger logger = LoggerFactory.getLogger(SpringAiImageGeneratorClient.class);
  private static final int DEFAULT_TIMEOUT_MS = 180_000;

  /** The adapter always asks for one image ({@code n(1)} below); the meter reports the same. */
  private static final int SINGLE_IMAGE = 1;

  /** stable-diffusion.cpp's own default, used when the deployment did not state one. */
  private static final int DEFAULT_STEPS = 20;

  private static final JsonMapper EXTRA_ARGS_JSON = JsonMapper.builder().build();

  private final CoreProperties properties;
  private final CatalogModelManager catalogModelManager;

  /**
   * Constructs the adapter with the CatalogModelManager and CoreProperties.
   *
   * @param properties Core configuration properties.
   * @param catalogModelManager The catalog model manager.
   */
  public SpringAiImageGeneratorClient(
      CoreProperties properties, CatalogModelManager catalogModelManager) {
    this.properties = Objects.requireNonNull(properties, "CoreProperties must not be null");
    this.catalogModelManager =
        Objects.requireNonNull(catalogModelManager, "CatalogModelManager must not be null");
  }

  @Override
  public ImageResponse generateImage(ImageRequest request) {
    int height = request.height() != null ? request.height() : 512;
    int width = request.width() != null ? request.width() : 512;

    String activeModel = resolveModel(request.model());
    Optional<CatalogModelDto> entry = catalogEntry(activeModel);
    String providerName = entry.map(CatalogModelDto::providerName).orElse("localai-image");
    String baseUrl = resolveBaseUrl(providerName);
    String apiKey = resolveApiKey(providerName);
    int steps = resolveSteps(request.direction(), entry);
    boolean diffusionCpp = takesDiffusionArgs(request.direction(), entry);

    logger.info(
        "SpringAiImageGeneratorClient: model {} at {}x{}, {} steps, via {}",
        activeModel,
        width,
        height,
        steps,
        baseUrl);

    if (!request.references().isEmpty()) {
      // References are pictures, not prompt text: they go in a JSON body (sd-server's sdapi
      // route), never inside the prompt's extra-args block, whose regex is not made for megabytes.
      int accepted =
          ReferenceImages.maxReferences(entry.map(CatalogModelDto::options).orElse(null));
      ReferenceImages.requireAccepted(activeModel, request.references().size(), accepted);
      byte[] referenced =
          ReferenceImages.generate(
              ReferenceImages.serverRoot(baseUrl),
              apiKey,
              request,
              width,
              height,
              steps,
              Duration.ofMillis(resolveTimeout(false)));
      requireRealImage(referenced, null);
      return new ImageResponse(
          referenced,
          buildImageDataUrl(referenced),
          "png",
          measuredUsage(referenced, steps),
          ReferenceImages.weightNotice(activeModel, request.references()));
    }

    ImageModel dynamicImageModel = buildImageModel(baseUrl, apiKey, activeModel, height, width);

    OpenAiImageOptions executionOptions =
        OpenAiImageOptions.builder()
            .model(activeModel)
            .n(1)
            .height(height)
            .width(width)
            // The timeout of the CALL: the SDK applies the request options' timeout, whatever the
            // client was built with, and its default is 60 s — a diffusion engine sends nothing
            // until
            // its image is done (about a minute for 1024x1024 on a Mac). Without this the
            // configured
            // IMAGE_GEN_READ_TIMEOUT_MS was ignored and a slow image became a timeout.
            .timeout(Duration.ofMillis(resolveTimeout(false)))
            .build();

    // A hosted model (DigitalOcean's Stable Diffusion 3.5, OpenAI's image models) has no
    // `<sd_cpp_extra_args>`: sent to it the block
    // would be drawn into the picture as text. It is sent only to a model that declares its steps
    // (a diffusion.cpp engine).
    String prompt =
        diffusionCpp ? request.prompt() + extraArgs(request.direction(), steps) : request.prompt();
    var response = dynamicImageModel.call(new ImagePrompt(prompt, executionOptions));
    var generation = response.getResult();
    byte[] imageData = extractImageData(generation);
    String url = extractUrl(generation);
    requireRealImage(imageData, url);

    if (imageData.length > 0 && url == null) {
      url = buildImageDataUrl(imageData);
    }
    return new ImageResponse(
        imageData, url, "png", measuredUsage(imageData, diffusionCpp ? steps : 1));
  }

  /** A picture of a real size: a 1x1 PNG is 70 bytes, a 512x512 one is tens of kilobytes. */
  private static final int MIN_IMAGE_BYTES = 256;

  /**
   * Refuses what is not a picture: no bytes and no URL, or a few bytes (a probe or placeholder
   * image). A run must fail by name rather than seal such a file as its output [§12 Zero-Fallback].
   *
   * @throws IllegalStateException when the engine returned nothing that is a picture
   */
  static void requireRealImage(byte[] imageData, String url) {
    int size = imageData == null ? 0 : imageData.length;
    if (size == 0 && url == null) {
      throw new IllegalStateException("the image engine returned no image");
    }
    if (size > 0 && size < MIN_IMAGE_BYTES) {
      throw new IllegalStateException(
          "the image engine returned an image of " + size + " bytes — not a picture");
    }
  }

  /**
   * The {@code <sd_cpp_extra_args>} block: what the OpenAI images API has no field for.
   *
   * <p>The step count is <b>always</b> sent, so the count the meter reports is the count the server
   * was told to run — a fact about this request, not a guess about how the server was launched.
   * That closes the coupling ADR-047 recorded as this capability's weakest link (one env var, two
   * readers). The seed and the negative prompt are sent only when the caller gave them.
   */
  static String extraArgs(ImageDirection direction, int steps) {
    Map<String, Object> args = new LinkedHashMap<>();
    if (direction.seed() != null) {
      args.put("seed", direction.seed());
    }
    if (direction.negativePrompt() != null) {
      args.put("negative_prompt", direction.negativePrompt());
    }
    args.put("sample_params", Map.of("sample_steps", steps));
    return "<sd_cpp_extra_args>"
        + EXTRA_ARGS_JSON.writeValueAsString(args)
        + "</sd_cpp_extra_args>";
  }

  /**
   * What IMAGE_STEP ({@code images × steps × megapixels}) is billed on, measured on the image the
   * provider returned [BILL-002].
   *
   * <p>It used to report the width and height this adapter <i>asked</i> for — a quantity taken from
   * the caller's own request, the same class of error as a scope a caller declares about itself. A
   * provider that returned a larger image billed the smaller one, and one that returned no image
   * billed a full one (ADR-062 §5). Now an image that cannot be decoded is unmeasured, and an
   * unmeasured generation releases its hold rather than being billed a guess.
   *
   * <p>{@code steps} is the input no image carries. It is the count this adapter sent in the
   * request ({@link #extraArgs}), so it is a fact about what the server was asked to run rather
   * than a reading of how it was launched.
   */
  private ImageUsage measuredUsage(byte[] imageData, int steps) {
    if (imageData.length == 0) {
      return ImageUsage.none();
    }
    try {
      java.awt.image.BufferedImage decoded =
          javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageData));
      if (decoded == null) {
        return ImageUsage.none();
      }
      return new ImageUsage(SINGLE_IMAGE, steps, decoded.getWidth(), decoded.getHeight());
    } catch (java.io.IOException unreadable) {
      logger.warn("Generated image could not be decoded; the generation is unmeasured", unreadable);
      return ImageUsage.none();
    }
  }

  /**
   * The denoising-step count to ask for: the caller's, else the model's own (the catalogue's {@code
   * max_steps} — a distilled model is trained for its count and degrades away from it), else the
   * deployment's {@code orazaka.core.image.generation.steps}, else stable-diffusion.cpp's default.
   */
  private int resolveSteps(ImageDirection direction, Optional<CatalogModelDto> entry) {
    if (direction.steps() != null) {
      return direction.steps();
    }
    Integer catalogued = entry.map(CatalogModelDto::maxSteps).orElse(null);
    if (catalogued != null && catalogued > 0) {
      return catalogued;
    }
    if (properties.image() != null
        && properties.image().generation() != null
        && properties.image().generation().steps() != null
        && properties.image().generation().steps() > 0) {
      return properties.image().generation().steps();
    }
    return DEFAULT_STEPS;
  }

  private String resolveModel(String requestModel) {
    if (requestModel != null && !requestModel.isBlank()) {
      return requestModel;
    }
    return catalogModelManager
        .getDefaultModelByCategory(MediaCategory.IMAGE.value())
        .map(CatalogModelDto::modelName)
        .orElse("stable-diffusion");
  }

  private Optional<CatalogModelDto> catalogEntry(String activeModel) {
    return catalogModelManager.getModelsByCategory(MediaCategory.IMAGE.value()).stream()
        .filter(m -> m.modelName().equalsIgnoreCase(activeModel))
        .findFirst();
  }

  private String resolveBaseUrl(String providerName) {
    String baseUrl = catalogModelManager.getProviderBaseUrl(providerName);
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = resolveBaseUrlFromProperties();
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new CoreException(
          "Missing required configuration: image generation baseUrl. "
              + "Configure via orazaka.core.image.generation.base-url or register a provider in the catalog.");
    }
    return baseUrl;
  }

  private String resolveBaseUrlFromProperties() {
    if (properties.image() != null
        && properties.image().generation() != null
        && properties.image().generation().baseUrl() != null) {
      return properties.image().generation().baseUrl();
    }
    return null;
  }

  /**
   * Whether this model is a diffusion.cpp engine that takes steps, a seed and a negative prompt in
   * the prompt's {@code <sd_cpp_extra_args>} block: one the catalogue does not describe (the
   * deployment default), one that declares its steps, or a request that asks for steps itself. A
   * catalogued model with no steps is a hosted one, which gets the prompt as it is.
   */
  static boolean takesDiffusionArgs(ImageDirection direction, Optional<CatalogModelDto> entry) {
    if (entry.isEmpty() || direction.steps() != null) {
      return true;
    }
    Integer declared = entry.get().maxSteps();
    return declared != null && declared > 0;
  }

  /** The key of the model's provider (the catalogue's, per provider), else the deployment's. */
  private String resolveApiKey(String providerName) {
    String providerKey = catalogModelManager.getProviderApiKey(providerName);
    if (providerKey != null && !providerKey.isBlank() && !"not-required".equals(providerKey)) {
      return providerKey;
    }
    return resolveApiKey();
  }

  private String resolveApiKey() {
    if (properties.image() != null
        && properties.image().generation() != null
        && properties.image().generation().apiKey() != null) {
      return properties.image().generation().apiKey();
    }
    return "not-required";
  }

  private ImageModel buildImageModel(
      String baseUrl, String apiKey, String activeModel, int height, int width) {
    // Spring AI 2.0 derives the com.openai client from the options via OpenAiSetup, so the provider
    // endpoint (base-url + api-key) and the long read timeout — critical for local diffusion
    // backends
    // — are set on the OpenAiImageOptions.
    OpenAiImageOptions executionOptions =
        OpenAiImageOptions.builder()
            .model(activeModel)
            .n(1)
            .height(height)
            .width(width)
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .timeout(Duration.ofMillis(resolveTimeout(false)))
            .build();

    return new SafeImageModel(OpenAiImageModel.builder().options(executionOptions).build());
  }

  private int resolveTimeout(boolean isConnect) {
    if (properties.image() != null && properties.image().generation() != null) {
      var gen = properties.image().generation();
      Integer timeout = isConnect ? gen.connectTimeoutMs() : gen.readTimeoutMs();
      if (timeout != null) {
        return timeout;
      }
    }
    return DEFAULT_TIMEOUT_MS;
  }

  private byte[] extractImageData(ImageGeneration generation) {
    if (generation == null || generation.getOutput() == null) {
      return new byte[0];
    }
    byte[] data = decodeBase64Image(generation.getOutput().getB64Json());
    if (data.length == 0) {
      data = extractImageViaReflection(generation.getOutput());
    }
    return data;
  }

  private String extractUrl(ImageGeneration generation) {
    if (generation == null || generation.getOutput() == null) {
      return null;
    }
    return generation.getOutput().getUrl();
  }

  private byte[] decodeBase64Image(String b64) {
    if (b64 == null || b64.isBlank()) {
      return new byte[0];
    }
    try {
      return Base64.getDecoder().decode(b64.trim());
    } catch (IllegalArgumentException e) {
      logger.warn("Failed to decode base64 image data", e);
      return new byte[0];
    }
  }

  private byte[] extractImageViaReflection(Object output) {
    try {
      var method = output.getClass().getMethod("getImage");
      Object imgObj = method.invoke(output);
      if (imgObj instanceof byte[] bytes) {
        return bytes;
      }
    } catch (ReflectiveOperationException e) {
      logger.trace("Failed reflective fallback extraction via getImage()", e);
    }
    return new byte[0];
  }

  private String buildImageDataUrl(byte[] imageData) {
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageData);
  }
}
