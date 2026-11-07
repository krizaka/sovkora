package com.krizaka.sovkora.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

/**
 * The voice turn and the session summary through the <b>running</b> stack (ADR-076): browser → edge
 * → conversation service → the engine's pipeline, the studio service, Whisper and Piper on LocalAI
 * and a chat model on Ollama.
 *
 * <p>Four scenarios, the ones ADR-076 names: a text turn, an audio turn, a turn the crisis guard
 * answers with the wellbeing pack's reviewed text and Québec's line, and a {@code session-summary}
 * run whose output is held to its JSON contract.
 *
 * <p>Tagged {@code voice}: it needs the native runtimes (LocalAI with whisper-base and the Piper
 * voices, Ollama with a chat model), which the hermetic stack does not start. Run it with {@code
 * -De2e.voice}; without them it fails loudly rather than skipping, because a voice gate that passes
 * without a voice proves nothing.
 */
@Tag("voice")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VoiceTurnIT extends AbstractApiE2eTest {

  /** The latency ADR-076 promises on the Mac: first spoken sentence after the upload ends. */
  private static final long FIRST_SENTENCE_BUDGET_MS = 2_500;

  private static final Set<String> NAMES =
      Set.of("transcript", "delta", "sentence", "safety.crisis", "scope.refused", "done", "error");

  private String token;
  private String installationId;

  /** One SSE frame. */
  private record Frame(String name, JsonNode data) {}

  @BeforeAll
  void installTheWellbeingStudio() {
    token = authenticate();
    APIResponse installed =
        request.post(
            "/api/v1/studios/session-summary/installations",
            bearerJson(
                token,
                "{\"config\":{},\"consentVersion\":\"1.0\",\"ageAttested\":true,\"region\":\"CA-QC\"}"));
    assertEquals(
        201,
        installed.status(),
        "session-summary must install with consent in CA-QC: " + installed.text());
    installationId = MAPPER.readTree(installed.text()).path("id").asString();
  }

  private List<Frame> turn(FormData form) {
    APIResponse response =
        request.post(
            "/api/v1/voice/turns",
            RequestOptions.create()
                .setHeader("Authorization", "Bearer " + token)
                .setMultipart(form)
                .setTimeout(120_000));
    assertEquals(200, response.status(), "the voice turn must open its stream: " + response.text());
    List<Frame> frames = new ArrayList<>();
    String name = null;
    for (String line : response.text().split("\n")) {
      if (line.startsWith("event:")) {
        name = line.substring(6).trim();
      } else if (line.startsWith("data:") && name != null) {
        assertTrue(NAMES.contains(name), "an event outside the contract: " + name);
        frames.add(new Frame(name, MAPPER.readTree(line.substring(5))));
        name = null;
      }
    }
    assertFalse(frames.isEmpty(), "the stream carried no event");
    assertTrue(
        frames.stream().noneMatch(frame -> "error".equals(frame.name())),
        "the turn failed: " + frames);
    assertEquals("done", frames.getLast().name(), "every turn ends with done");
    return frames;
  }

  private FormData form(String conversation) {
    return FormData.create()
        .set("conversationId", conversation)
        .set("locale", "fr-CA")
        .set("installationId", installationId)
        .set("context", "Le fil : la dernière fois, garder le jeudi soir pour soi.");
  }

  private static List<JsonNode> named(List<Frame> frames, String name) {
    return frames.stream().filter(frame -> name.equals(frame.name())).map(Frame::data).toList();
  }

  private void assertSpoken(JsonNode audio) {
    String url = audio.path("url").asString();
    assertTrue(url.startsWith("/api/v1/voice/clips/"), "a signed clip URL, got " + url);
    APIResponse clip = request.get(url);
    assertEquals(200, clip.status(), "the signed clip must play without a token");
    assertEquals("audio/wav", clip.headers().get("content-type"));
    assertTrue(clip.body().length > 1_000, "a clip has audio in it");
    assertTrue(audio.path("durationMs").asLong() > 0, "a WAV's duration is measured");
    assertEquals(404, request.get(url.replaceAll("signature=\\w+", "signature=00")).status());
  }

  @Test
  @Order(1)
  @DisplayName("A text turn: the answer is written, then spoken sentence by sentence")
  void textTurn() {
    List<Frame> frames =
        turn(form("e2e-voice-text").set("text", "Bonsoir. La semaine a été lourde."));

    assertTrue(named(frames, "transcript").isEmpty(), "a text turn is not transcribed");
    assertFalse(named(frames, "delta").isEmpty(), "the answer streams as it is written");
    List<JsonNode> sentences = named(frames, "sentence");
    assertFalse(sentences.isEmpty(), "the answer is spoken");
    assertEquals(0, sentences.getFirst().path("index").asInt());
    assertFalse(
        sentences.getFirst().path("text").asString().isBlank(), "a caption with each sentence");
    assertFalse(sentences.getFirst().has("words"), "no word timing is invented (ADR-076 §4)");
    assertSpoken(sentences.getFirst().path("audio"));
    JsonNode done = frames.getLast().data();
    assertTrue(done.path("usage").path("ttsCharacters").asLong() > 0);
    assertTrue(done.path("timings").path("firstSentenceMs").asLong() > 0);
  }

  @Test
  @Order(2)
  @DisplayName("An audio turn: heard, answered and spoken — first sentence within 2.5 s once warm")
  void audioTurn() throws IOException {
    byte[] recording;
    try (InputStream in = VoiceTurnIT.class.getResourceAsStream("/voice/utterance-fr.wav")) {
      assertNotNull(in, "the audio fixture is on the test classpath");
      recording = in.readAllBytes();
    }
    long best = Long.MAX_VALUE;
    for (int attempt = 1; attempt <= 2; attempt++) {
      List<Frame> frames =
          turn(
              form("e2e-voice-audio-" + attempt)
                  .set("audio", new FilePayload("utterance-fr.wav", "audio/wav", recording)));

      JsonNode transcript = named(frames, "transcript").getFirst();
      String heard = transcript.path("text").asString().toLowerCase();
      assertTrue(heard.contains("semaine") && heard.contains("enfants"), "heard: " + heard);
      assertTrue(transcript.path("seconds").asDouble() > 5.0, "the recording is ~6.5 s");
      assertSpoken(named(frames, "sentence").getFirst().path("audio"));
      JsonNode done = frames.getLast().data();
      assertTrue(done.path("usage").path("sttSeconds").asDouble() > 5.0, "minutes are measured");
      long firstSentence = done.path("timings").path("firstSentenceMs").asLong();
      System.out.printf(
          "[voice] audio turn %d: stt=%d ms, first delta=%d ms, first sentence=%d ms, total=%d ms%n",
          attempt,
          done.path("timings").path("sttMs").asLong(),
          done.path("timings").path("firstDeltaMs").asLong(),
          firstSentence,
          done.path("timings").path("totalMs").asLong());
      best = Math.min(best, firstSentence);
    }
    assertTrue(
        best < FIRST_SENTENCE_BUDGET_MS,
        "first spoken sentence must come within " + FIRST_SENTENCE_BUDGET_MS + " ms, was " + best);
  }

  @Test
  @Order(3)
  @DisplayName(
      "A crisis turn: the pack's reviewed text and Québec's line, spoken — never generated")
  void crisisTurn() {
    List<Frame> frames = turn(form("e2e-voice-crisis").set("text", "Je pense à en finir ce soir."));

    assertTrue(named(frames, "delta").isEmpty(), "no model wrote a word of this answer");
    assertTrue(named(frames, "sentence").isEmpty(), "no generated sentence is spoken");
    JsonNode crisis = named(frames, "safety.crisis").getFirst();
    assertTrue(
        crisis.path("response").asString().startsWith("Je m'arrête là"), "the reviewed text");
    JsonNode line = crisis.path("resource");
    assertEquals("CA-QC", line.path("region").asString());
    assertTrue(line.path("contact").asString().contains("1 866 APPELLE"));
    assertTrue(line.path("contact").asString().contains("811"));
    assertTrue(line.path("sourceUrl").asString().startsWith("https://www.quebec.ca/"));
    assertEquals("2026-10-11", line.path("verifiedOn").asString());
    assertTrue(crisis.path("text").asString().contains("1 866 APPELLE"), "the line is spoken");
    assertSpoken(crisis.path("audio"));
  }

  @Test
  @Order(4)
  @DisplayName("session-summary: what the person keeps, as JSON held to its contract")
  void sessionSummary() throws InterruptedException {
    APIResponse started =
        request.post(
            "/api/v1/studios/installations/" + installationId + "/runs",
            bearerJson(
                token,
                """
                {"inputs":{"locale":"fr-CA","moodBefore":2,"moodAfter":3,
                 "thread":"La dernière fois : garder le jeudi soir pour soi.",
                 "turns":"Personne : Cette semaine a été lourde au travail, et ce soir je suis fatiguée avec les enfants.\\nPrésence : Qu'est-ce qui a pesé le plus ?\\nPersonne : Les réunions tard. En parler m'aide un peu. Je vais bloquer jeudi 19 h dans mon agenda, et j'aimerais parler de cette fatigue à ma thérapeute."}}
                """));
    assertTrue(started.status() == 201 || started.status() == 202, started.text());
    String runId = MAPPER.readTree(started.text()).path("id").asString();

    JsonNode run = null;
    for (int poll = 0; poll < 60; poll++) {
      run = MAPPER.readTree(request.get("/api/v1/studios/runs/" + runId, bearer(token)).text());
      String status = run.path("status").asString();
      if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
        break;
      }
      Thread.sleep(2_000);
    }
    assertNotNull(run);
    assertEquals("SUCCEEDED", run.path("status").asString(), "run: " + run);
    JsonNode output = run.path("outputs").get(0);
    assertEquals("summary", output.path("key").asString());
    assertEquals("JSON", output.path("type").asString());
    JsonNode summary = MAPPER.readTree(output.path("value").asString());
    assertTrue(summary.path("themes").isArray() && summary.path("themes").size() <= 3);
    assertTrue(summary.path("bringToTherapist").isArray());
    assertFalse(summary.path("smallStep").asString().isBlank());
    assertTrue(summary.has("whatHelped"));
    assertEquals("fr-CA", summary.path("locale").asString());
    assertFalse(summary.has("mood"), "the contract refuses anything it does not declare");
  }
}
