package com.krizaka.sovkora.adapter.springai.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.image.ImageDirection;
import com.krizaka.sovkora.core.domain.model.image.ImageRequest;
import com.krizaka.sovkora.core.domain.model.image.ImageUsage;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import com.krizaka.sovkora.persistence.domain.model.CatalogModelDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [BILL-002] — the IMAGE_STEP quantity follows the work, not the request.
 *
 * <p>A divergence contract, through the real client and its real provider SDK, against a stub of
 * the OpenAI-compatible images endpoint: the request asks for one size and the provider returns
 * another. The bill must follow the image that came back. A test that built an {@code ImageUsage}
 * by hand would pass whichever the adapter used — which is the whole lesson of ADR-062.
 *
 * <p>{@code steps} is the one input no image carries. The adapter now <i>sends</i> it on every
 * request (the {@code <sd_cpp_extra_args>} block of stable-diffusion.cpp), so the count billed is
 * the count the server was asked to run — the caller's, else the catalogue's, else the
 * deployment's.
 */
public class SpringAiImageGeneratorClientTest {

  private HttpServer provider;
  private final AtomicReference<String> received = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<com.sun.net.httpserver.Headers> headers = new AtomicReference<>();

  @AfterEach
  void stop() {
    if (provider != null) {
      provider.stop(0);
    }
  }

  private String serve(String body) throws IOException {
    provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    provider.createContext(
        "/",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          headers.set(exchange.getRequestHeaders());
          received.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    provider.start();
    return "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1";
  }

  private static String png(int width, int height) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
    return Base64.getEncoder().encodeToString(bytes.toByteArray());
  }

  private static SpringAiImageGeneratorClient client(String baseUrl, int steps) {
    return client(baseUrl, steps, List.of());
  }

  private static SpringAiImageGeneratorClient client(
      String baseUrl, int steps, List<CatalogModelDto> catalogued) {
    return client(baseUrl, steps, catalogued, null);
  }

  private static SpringAiImageGeneratorClient client(
      String baseUrl, int steps, List<CatalogModelDto> catalogued, String providerKey) {
    CoreProperties properties =
        new CoreProperties(
            "ollama",
            null,
            null,
            null,
            null,
            new CoreProperties.ImageConfig(
                new CoreProperties.ImageGenerationConfig(
                    "localai-image",
                    baseUrl,
                    "not-required",
                    "stable-diffusion",
                    512,
                    512,
                    1,
                    steps,
                    5000,
                    5000)),
            null,
            null);
    CatalogModelManager catalog = mock(CatalogModelManager.class);
    when(catalog.getDefaultModelByCategory(anyString())).thenReturn(Optional.empty());
    when(catalog.getModelsByCategory(anyString())).thenReturn(catalogued);
    when(catalog.getProviderBaseUrl(anyString())).thenReturn(null);
    when(catalog.getProviderApiKey(anyString())).thenReturn(providerKey);
    return new SpringAiImageGeneratorClient(properties, catalog);
  }

  private static ImageRequest askFor(int width, int height) {
    return new ImageRequest(
        "a lighthouse at dusk", width, height, "stable-diffusion", Map.of(), Context.anonymous());
  }

  @Test
  @DisplayName(
      "[BILL-002] asked for 512×512, returned 768×512: the bill measures the image that came back")
  void theBilledDimensionsAreTheReturnedImagesNotTheRequests() throws IOException {
    String baseUrl =
        serve("{\"created\":1700000000,\"data\":[{\"b64_json\":\"" + png(768, 512) + "\"}]}");

    ImageUsage usage = client(baseUrl, 20).generateImage(askFor(512, 512)).usage();

    assertThat(usage.reported()).isTrue();
    assertThat(usage.width())
        .as("width billed from something other than the image that came back")
        .isEqualTo(768);
    assertThat(usage.height()).isEqualTo(512);
    assertThat(usage.images()).isEqualTo(1);
    assertThat(usage.steps()).isEqualTo(20);
  }

  @Test
  @DisplayName(
      "[BILL-002] a provider that returns no decodable image is unmeasured — released, never billed at the request's size")
  void noDecodableImageIsUnmeasured() throws IOException {
    String baseUrl =
        serve("{\"created\":1700000000,\"data\":[{\"url\":\"http://127.0.0.1:1/nowhere.png\"}]}");

    ImageUsage usage = client(baseUrl, 20).generateImage(askFor(512, 512)).usage();

    assertThat(usage.reported()).isFalse();
  }

  @Test
  @DisplayName(
      "the size, the seed, the negative prompt and the steps reach stable-diffusion.cpp, and the meter bills the steps sent")
  void directionReachesTheServerAndTheMeter() throws IOException {
    String baseUrl =
        serve("{\"created\":1700000000,\"data\":[{\"b64_json\":\"" + png(1024, 1280) + "\"}]}");
    ImageRequest request =
        new ImageRequest(
            "a tea glass on a wooden table",
            1024,
            1280,
            "stable-diffusion",
            Map.of(),
            Context.anonymous(),
            new ImageDirection("text, logo", 4242L, 6));

    ImageUsage usage = client(baseUrl, 20).generateImage(request).usage();

    assertThat(received.get())
        .contains("\"size\":\"1024x1280\"")
        .contains("<sd_cpp_extra_args>")
        .contains("\\\"seed\\\":4242")
        .contains("\\\"negative_prompt\\\":\\\"text, logo\\\"")
        .contains("\\\"sample_steps\\\":6");
    assertThat(usage.steps())
        .as("billed a step count the server was not asked to run")
        .isEqualTo(6);
  }

  @Test
  @DisplayName(
      "without a requested count, a catalogued model runs — and is billed — at its own step count")
  void aCataloguedModelRunsAtItsOwnStepCount() throws IOException {
    String baseUrl =
        serve("{\"created\":1700000000,\"data\":[{\"b64_json\":\"" + png(1024, 1024) + "\"}]}");
    CatalogModelDto turbo =
        new CatalogModelDto(
            1,
            "z-image-turbo",
            "Z-Image Turbo",
            "image",
            null,
            true,
            "localai-image",
            8,
            null,
            null);

    ImageUsage usage =
        client(baseUrl, 20, List.of(turbo))
            .generateImage(
                new ImageRequest(
                    "a lighthouse at dusk",
                    1024,
                    1024,
                    "z-image-turbo",
                    Map.of(),
                    Context.anonymous()))
            .usage();

    assertThat(received.get()).contains("\\\"sample_steps\\\":8").doesNotContain("seed");
    assertThat(usage.steps()).isEqualTo(8);
  }

  private static CatalogModelDto hosted(String name, Integer maxSteps) {
    return new CatalogModelDto(
        1, name, name, "image", null, false, "do-inference", maxSteps, null, null);
  }

  @Test
  @DisplayName(
      "a hosted model (no declared steps) gets the prompt as it is — no <sd_cpp_extra_args> drawn into the picture")
  void aHostedModelGetsThePromptWithoutTheDiffusionCppBlock() throws IOException {
    String baseUrl =
        serve("{\"created\":1700000000,\"data\":[{\"b64_json\":\"" + png(512, 512) + "\"}]}");
    var catalogued = List.of(hosted("stable-diffusion-3.5-large", null));
    var request =
        new ImageRequest(
            "a lighthouse at dusk",
            512,
            512,
            "stable-diffusion-3.5-large",
            Map.of(),
            Context.anonymous());

    ImageUsage usage = client(baseUrl, 20, catalogued).generateImage(request).usage();

    assertThat(received.get()).contains("a lighthouse at dusk").doesNotContain("sd_cpp_extra_args");
    assertThat(usage.steps()).as("a model without steps is metered as one step").isEqualTo(1);
  }

  @Test
  @DisplayName(
      "a diffusion.cpp model (declared steps) still gets its steps in the prompt's extra-args block")
  void aModelThatDeclaresStepsStillGetsTheBlock() throws IOException {
    String baseUrl =
        serve("{\"created\":1700000000,\"data\":[{\"b64_json\":\"" + png(512, 512) + "\"}]}");
    var catalogued = List.of(hosted("flux-2-klein-4b", 4));
    var request =
        new ImageRequest(
            "a lighthouse at dusk", 512, 512, "flux-2-klein-4b", Map.of(), Context.anonymous());

    ImageUsage usage = client(baseUrl, 20, catalogued).generateImage(request).usage();

    assertThat(received.get()).contains("sd_cpp_extra_args").contains("\\\"sample_steps\\\":4");
    assertThat(usage.steps()).isEqualTo(4);
  }

  @Test
  @DisplayName("the key of the model's provider is the one sent; without one, the deployment's")
  void theProvidersKeyIsSent() throws IOException {
    String baseUrl =
        serve("{\"created\":1700000000,\"data\":[{\"b64_json\":\"" + png(512, 512) + "\"}]}");
    var catalogued = List.of(hosted("stable-diffusion-3.5-large", null));
    var request =
        new ImageRequest(
            "a lighthouse at dusk",
            512,
            512,
            "stable-diffusion-3.5-large",
            Map.of(),
            Context.anonymous());

    client(baseUrl, 20, catalogued, "provider-secret").generateImage(request);
    assertThat(authorization.get()).isEqualTo("Bearer provider-secret");

    client(baseUrl, 20, catalogued, "not-required").generateImage(request);
    assertThat(authorization.get()).isEqualTo("Bearer not-required");
  }

  @org.junit.jupiter.api.Test
  void requireRealImage_refusesAOnePixelPlaceholder() {
    byte[] onePixel = new byte[70]; // what a 1x1 transparent PNG weighs
    IllegalStateException refused =
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> SpringAiImageGeneratorClient.requireRealImage(onePixel, null));
    org.junit.jupiter.api.Assertions.assertTrue(refused.getMessage().contains("70 bytes"));
  }

  @org.junit.jupiter.api.Test
  void requireRealImage_refusesNothingAtAll() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () -> SpringAiImageGeneratorClient.requireRealImage(new byte[0], null));
  }

  @org.junit.jupiter.api.Test
  void requireRealImage_acceptsAPictureOrAHostedUrl() {
    SpringAiImageGeneratorClient.requireRealImage(new byte[200_000], null);
    SpringAiImageGeneratorClient.requireRealImage(new byte[0], "https://cdn.example/x.png");
  }

  @Test
  @DisplayName(
      "the configured read timeout is the one the client uses — a diffusion engine is silent until its image is done")
  void theConfiguredReadTimeoutIsTheOneTheClientUses() throws IOException {
    String baseUrl =
        serve("{\"created\":1700000000,\"data\":[{\"b64_json\":\"" + png(512, 512) + "\"}]}");
    var request =
        new ImageRequest(
            "a lighthouse at dusk", 512, 512, "stable-diffusion", Map.of(), Context.anonymous());

    client(baseUrl, 20).generateImage(request);

    // The client states the read timeout it applies (the test's configuration says 5000 ms).
    assertThat(headers.get().getFirst("X-Stainless-Read-Timeout")).isEqualTo("5");
  }

  private static CatalogModelDto klein(String options) {
    return new CatalogModelDto(
        2,
        "flux-2-klein-4b",
        "FLUX.2 [klein] 4B",
        "image",
        options,
        true,
        "localai-image",
        4,
        null,
        null,
        null,
        "local",
        "available",
        null,
        null);
  }

  private static ImageRequest withReferences(double... weights) throws IOException {
    java.util.List<com.krizaka.sovkora.core.domain.model.image.ImageReference> references =
        new java.util.ArrayList<>();
    for (double weight : weights) {
      references.add(
          new com.krizaka.sovkora.core.domain.model.image.ImageReference(
              Base64.getDecoder().decode(png(64, 64)), "image/png", weight));
    }
    return new ImageRequest(
        "the same owl on a Montreal staircase",
        1360,
        768,
        "flux-2-klein-4b",
        Map.of(),
        Context.anonymous(),
        new ImageDirection(null, 7L, null),
        references);
  }

  @Test
  @DisplayName(
      "[#158] references go to sd-server's sdapi route as extra_images, in order, with the prompt untouched")
  void referencesReachTheServerAsExtraImages() throws IOException {
    String baseUrl = serve("{\"images\":[\"" + png(1360, 768) + "\"]}");

    var response =
        client(baseUrl, 20, List.of(klein("{\"maxReferences\":4}")))
            .generateImage(withReferences(1.0, 1.0));

    assertThat(received.get())
        .contains("\"extra_images\":[\"")
        .contains("\"seed\":7")
        .contains("\"steps\":4")
        .contains("\"width\":1360")
        .doesNotContain("sd_cpp_extra_args");
    assertThat(received.get().split("iVBOR", -1)).as("two references sent").hasSize(3);
    assertThat(response.usage().width()).isEqualTo(1360);
    assertThat(response.notice()).isNull();
  }

  @Test
  @DisplayName(
      "[#158] a model whose catalogue entry declares no maxReferences refuses them by name")
  void aModelWithoutReferencesRefusesThem() throws IOException {
    String baseUrl = serve("{\"images\":[\"" + png(1360, 768) + "\"]}");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> client(baseUrl, 20, List.of(klein(null))).generateImage(withReferences(1.0)))
        .hasMessageContaining("flux-2-klein-4b does not take reference images");
    assertThat(received.get()).as("nothing sent to the engine").isNull();
  }

  @Test
  @DisplayName("[#158] more references than the model takes are refused before the engine")
  void tooManyReferencesAreRefused() throws IOException {
    String baseUrl = serve("{\"images\":[\"" + png(1360, 768) + "\"]}");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                client(baseUrl, 20, List.of(klein("{\"maxReferences\":1}")))
                    .generateImage(withReferences(1.0, 1.0)))
        .hasMessageContaining("takes at most 1 reference images, got 2");
  }

  @Test
  @DisplayName(
      "[#158] a weight the model cannot apply is said with the image, never dropped silently")
  void anUnappliedWeightIsSaid() throws IOException {
    String baseUrl = serve("{\"images\":[\"" + png(1360, 768) + "\"]}");

    var response =
        client(baseUrl, 20, List.of(klein("{\"maxReferences\":4}")))
            .generateImage(withReferences(0.5));

    assertThat(response.notice()).contains("full strength").contains("not applied");
  }

  @Test
  @DisplayName("[#158] the sdapi route hangs off the server root, with or without /v1")
  void serverRootDropsTheOpenAiSuffix() {
    assertThat(ReferenceImages.serverRoot("http://h:1/v1/")).isEqualTo("http://h:1");
    assertThat(ReferenceImages.serverRoot("http://h:1")).isEqualTo("http://h:1");
    assertThat(ReferenceImages.maxReferences("not json")).isZero();
    assertThat(ReferenceImages.maxReferences("{\"maxReferences\":3}")).isEqualTo(3);
  }
}
