package com.krizaka.sovkora.adapter.springai.image;

import com.krizaka.sovkora.core.domain.model.image.ImageReference;
import com.krizaka.sovkora.core.domain.model.image.ImageRequest;
import com.krizaka.sovkora.core.infrastructure.support.CoreException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Image generation conditioned on reference pictures (krizaka-internal#158): the same character,
 * prop or place in every shot.
 *
 * <p>stable-diffusion.cpp's {@code sd-server} takes references as {@code extra_images} on its
 * {@code /sdapi/v1/txt2img} route (FLUX.2 [klein] conditions on them; the OpenAI images route has
 * no field for them). Which model takes how many is catalogue data — {@code orazaka_models.options}
 * {@code {"maxReferences": n}} — never a list in code. A model that takes none refuses by name: a
 * reference is never silently dropped.
 */
final class ReferenceImages {

  /** The catalogue key that says how many references a model conditions on. */
  static final String MAX_REFERENCES_KEY = "maxReferences";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private ReferenceImages() {}

  /**
   * @param options the model's catalogue {@code options} JSON, or {@code null}
   * @return how many references the model takes; {@code 0} when it declares none or the JSON is not
   *     an object
   */
  static int maxReferences(String options) {
    if (options == null || options.isBlank()) {
      return 0;
    }
    try {
      JsonNode node = JSON.readTree(options).path(MAX_REFERENCES_KEY);
      return node.isIntegralNumber() ? Math.max(0, node.asInt()) : 0;
    } catch (RuntimeException unreadable) {
      return 0;
    }
  }

  private static String modelPhrase(String model) {
    return "the model " + model;
  }

  /**
   * @throws CoreException when the model takes no reference, or fewer than were sent
   */
  static void requireAccepted(String model, int sent, int accepted) {
    if (accepted == 0) {
      throw new CoreException(
          modelPhrase(model)
              + " does not take reference images: choose a model that does (its catalogue entry"
              + " declares maxReferences), or run without references");
    }
    if (sent > accepted) {
      throw new CoreException(
          modelPhrase(model) + " takes at most " + accepted + " reference images, got " + sent);
    }
  }

  /**
   * @return the notice said with the image when a weight below full could not be applied, else
   *     {@code null}
   */
  static String weightNotice(String model, List<ImageReference> references) {
    if (references.stream().noneMatch(ImageReference::partial)) {
      return null;
    }
    return modelPhrase(model)
        + " conditions on reference images at full strength: the weights were recorded with the"
        + " image, not applied";
  }

  /**
   * @param baseUrl the provider's OpenAI base URL (ending in {@code /v1} or not)
   * @return the server root the sdapi route hangs off
   */
  static String serverRoot(String baseUrl) {
    String root = baseUrl.strip();
    while (root.endsWith("/")) {
      root = root.substring(0, root.length() - 1);
    }
    return root.endsWith("/v1") ? root.substring(0, root.length() - "/v1".length()) : root;
  }

  /** The sdapi body: the prompt as it is (no extra-args block), the references in order. */
  static Map<String, Object> body(ImageRequest request, int width, int height, int steps) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("prompt", request.prompt());
    if (request.direction().negativePrompt() != null) {
      body.put("negative_prompt", request.direction().negativePrompt());
    }
    if (request.direction().seed() != null) {
      body.put("seed", request.direction().seed());
    }
    body.put("steps", steps);
    body.put("width", width);
    body.put("height", height);
    body.put("batch_size", 1);
    body.put(
        "extra_images",
        request.references().stream()
            .map(r -> Base64.getEncoder().encodeToString(r.image()))
            .toList());
    return body;
  }

  /**
   * @return the generated picture's bytes
   * @throws CoreException when the server refuses, answers with no image, or cannot be reached
   */
  static byte[] generate(
      String root,
      String apiKey,
      ImageRequest request,
      int width,
      int height,
      int steps,
      Duration timeout) {
    HttpResponse<String> response = post(root, apiKey, request, width, height, steps, timeout);
    if (response.statusCode() / 100 != 2) {
      throw new CoreException(
          "the image engine refused the reference images ("
              + response.statusCode()
              + "): "
              + abbreviate(response.body()));
    }
    JsonNode images = JSON.readTree(response.body()).path("images");
    if (!images.isArray() || images.isEmpty() || !images.get(0).isString()) {
      throw new CoreException("the image engine returned no image");
    }
    return Base64.getDecoder().decode(images.get(0).asString());
  }

  private static HttpResponse<String> post(
      String root,
      String apiKey,
      ImageRequest request,
      int width,
      int height,
      int steps,
      Duration timeout) {
    HttpRequest.Builder call =
        HttpRequest.newBuilder(URI.create(root + "/sdapi/v1/txt2img"))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    JSON.writeValueAsString(body(request, width, height, steps)),
                    StandardCharsets.UTF_8));
    if (apiKey != null && !apiKey.isBlank() && !"not-required".equals(apiKey)) {
      call.header("Authorization", "Bearer " + apiKey);
    }
    try (HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build()) {
      return client.send(call.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException unreachable) {
      throw new CoreException(
          "the image engine could not be reached: " + unreachable.getMessage(), unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new CoreException("interrupted while the image engine worked");
    }
  }

  private static String abbreviate(String body) {
    String text = body == null ? "" : body.strip();
    return text.length() > 300 ? text.substring(0, 300) + "…" : text;
  }
}
