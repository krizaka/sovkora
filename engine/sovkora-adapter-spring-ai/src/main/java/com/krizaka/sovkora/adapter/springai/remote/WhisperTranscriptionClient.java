package com.krizaka.sovkora.adapter.springai.remote;

import com.krizaka.sovkora.core.application.processing.ProcessedAudioPayload;
import com.krizaka.sovkora.core.domain.ports.outbound.AudioTranscriber;
import java.math.BigDecimal;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring {@link RestClient}-based helper for Whisper audio transcription calls.
 *
 * <p>Encapsulates the multipart/form-data upload to a LocalAI-compatible {@code
 * /v1/audio/transcriptions} endpoint using Spring-native {@link ByteArrayResource} — eliminating
 * all manual boundary forging.
 *
 * <p>Consumed by the core's local audio and video processors through the {@link AudioTranscriber}
 * port.
 */
public final class WhisperTranscriptionClient implements AudioTranscriber {

  private static final Logger logger = LoggerFactory.getLogger(WhisperTranscriptionClient.class);
  private static final int CONNECT_TIMEOUT_MS = 10_000;
  private static final int READ_TIMEOUT_MS = 60_000;

  private final RestClient.Builder restClientBuilder;
  private final ObjectMapper objectMapper;

  public WhisperTranscriptionClient(
      RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
    this.restClientBuilder =
        Objects.requireNonNull(restClientBuilder, "RestClient.Builder must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
  }

  /**
   * Sends a multipart transcription request to the Whisper-compatible endpoint.
   *
   * @param baseUrl The base URL of the transcription service (e.g. {@code http://localhost:8085}).
   * @param audioBytes The raw audio binary content (extracted from media).
   * @param filename The virtual filename for the upload (e.g. {@code speech.mp3}).
   * @param model The Whisper model identifier (e.g. {@code whisper-1}).
   * @return The transcript and, when the provider reports one, the duration of the source audio.
   * @throws IllegalStateException If the response is missing or unparseable.
   */
  @Override
  public ProcessedAudioPayload transcribe(
      String baseUrl, byte[] audioBytes, String filename, String model) {
    if (filename == null || !isAudioFile(filename)) {
      throw new IllegalArgumentException(
          "Whisper transcription only accepts audio files. Rejected: " + filename);
    }

    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
    requestFactory.setReadTimeout(READ_TIMEOUT_MS);

    RestClient client =
        restClientBuilder
            .clone()
            .baseUrl(withoutVersionSuffix(baseUrl))
            .requestFactory(requestFactory)
            .build();

    ByteArrayResource fileResource = new NamedByteArrayResource(audioBytes, filename);

    logger.info(
        "WhisperTranscriptionClient: Transcribing {} bytes via {} at {}...",
        audioBytes.length,
        model,
        baseUrl);

    // verbose_json carries `duration`, which is the quantity AUDIO_MINUTE prices — the measurement
    // transcription had nothing to report before (ADR-066). A provider that does not know the
    // format answers 4xx; that is not a reason to fail the job, so the plain request is retried and
    // the job settles unmeasured, which releases rather than guesses.
    String responseBody;
    try {
      responseBody = post(client, fileResource, model, "verbose_json");
    } catch (RestClientResponseException refused) {
      if (!refused.getStatusCode().is4xxClientError()) {
        throw refused;
      }
      logger.info(
          "Transcription provider does not accept verbose_json ({}); retrying without it — this"
              + " job reports no duration and settles unmeasured",
          refused.getStatusCode());
      responseBody = post(client, fileResource, model, null);
    }

    if (responseBody == null || responseBody.isBlank()) {
      throw new IllegalStateException("Whisper transcription response is empty");
    }

    // The body IS the transcript — what someone said. Its size is logged, never its text (ADR-064).
    // [LOG-001] cannot see this one: a raw HTTP body is a String, and nothing types it as content
    // before it is parsed.
    logger.info("Whisper transcription response received ({} chars)", responseBody.length());
    return parseTranscription(responseBody);
  }

  /**
   * The base the request path is joined to: the provider's own, without a trailing {@code /v1}. The
   * endpoint below carries {@code /v1/audio/transcriptions} itself, while the same provider row is
   * read as {@code …/v1} by clients that append only {@code /images/generations} or {@code
   * /audio/speech}: joined blindly, {@code …/v1} answered 404 from {@code
   * …/v1/v1/audio/transcriptions} (dev, 2026-10-20). So the path is joined once, with or without
   * the suffix.
   */
  static String withoutVersionSuffix(String baseUrl) {
    String trimmed = baseUrl.strip().replaceAll("/+$", "");
    return trimmed.replaceFirst("(?i)/v1$", "");
  }

  private String post(
      RestClient client, ByteArrayResource fileResource, String model, String responseFormat) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", fileResource);
    body.add("model", model);
    if (responseFormat != null) {
      body.add("response_format", responseFormat);
    }
    return client
        .post()
        .uri("/v1/audio/transcriptions")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(body)
        .retrieve()
        .body(String.class);
  }

  private boolean isAudioFile(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".mp3")
        || lower.endsWith(".wav")
        || lower.endsWith(".m4a")
        || lower.endsWith(".ogg")
        || lower.endsWith(".flac")
        || lower.endsWith(".aac")
        || lower.endsWith(".webm");
  }

  private ProcessedAudioPayload parseTranscription(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      BigDecimal duration =
          root.hasNonNull("duration") ? new BigDecimal(root.get("duration").asString()) : null;
      if (root.has("text")) {
        return new ProcessedAudioPayload(root.get("text").asString(), duration);
      }
      return new ProcessedAudioPayload(responseBody, duration);
    } catch (Exception e) {
      logger.warn("Failed to parse Whisper JSON response, returning raw body", e);
      return new ProcessedAudioPayload(responseBody, null);
    }
  }

  /**
   * Named {@link ByteArrayResource} subclass that provides a filename for multipart uploads.
   * Replaces anonymous subclass to comply with ArchUnit governance mandate.
   */
  static final class NamedByteArrayResource extends ByteArrayResource {

    private final String filename;

    NamedByteArrayResource(byte[] byteArray, String filename) {
      super(byteArray);
      this.filename = filename;
    }

    @Override
    public String getFilename() {
      return filename;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof NamedByteArrayResource other)) {
        return false;
      }
      if (!super.equals(other)) {
        return false;
      }
      return Objects.equals(filename, other.filename);
    }

    @Override
    public int hashCode() {
      return 31 * super.hashCode() + Objects.hashCode(filename);
    }
  }
}
