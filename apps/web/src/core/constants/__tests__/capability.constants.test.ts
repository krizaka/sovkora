/**
 * @file capability.constants.test.ts
 * @description Tests for the capability constants module — pure function resolvers.
 */

import {
  resolveProviderFromFeature,
  resolveMediaKind,
  resolveDefaultModel,
  NODE_ID,
  MODEL_CATEGORY,
  DEFAULT_MODEL,
  DEFAULT_VOICE,
  DEFAULT_PROVIDER,
  CATEGORY_VALUES,
  NODE_TO_CATEGORY,
  FALLBACK_MODELS,
  COMMERCIAL_DEFAULTS,
  NODE_ICON,
} from "@/core/constants/capability.constants";

// ── resolveProviderFromFeature ───────────────────────────────────────────────

describe("resolveProviderFromFeature", () => {
  test("resolves video provider", () => {
    expect(resolveProviderFromFeature("ai.media.video")).toBe("localai-video");
  });

  test("resolves image provider", () => {
    expect(resolveProviderFromFeature("ai.image.generate")).toBe("localai-image");
  });

  test("resolves speech provider", () => {
    expect(resolveProviderFromFeature("ai.speech.tts")).toBe("localai");
  });

  test("returns localai as default", () => {
    expect(resolveProviderFromFeature("unknown")).toBe("localai");
  });
});

// ── resolveMediaKind ─────────────────────────────────────────────────────────

describe("resolveMediaKind", () => {
  test("resolves image from id containing 'image'", () => {
    expect(resolveMediaKind("orazaka.core.chat.image")).toBe("image");
  });

  test("resolves image from id containing 'vision'", () => {
    expect(resolveMediaKind("orazaka.core.media.vision")).toBe("image");
  });

  test("resolves audio from id containing 'speech'", () => {
    expect(resolveMediaKind("orazaka.core.media.speech")).toBe("audio");
  });

  test("resolves audio from id containing 'tts'", () => {
    expect(resolveMediaKind("some.tts.feature")).toBe("audio");
  });

  test("resolves audio from id containing 'audio'", () => {
    expect(resolveMediaKind("orazaka.core.media.audio.analysis")).toBe("audio");
  });

  test("resolves from icon token when id has no match", () => {
    expect(resolveMediaKind("unknown", "image")).toBe("image");
    expect(resolveMediaKind("unknown", "mic")).toBe("audio");
  });

  test("returns text as default", () => {
    expect(resolveMediaKind("unknown")).toBe("text");
  });
});

// ── resolveDefaultModel ──────────────────────────────────────────────────────

describe("resolveDefaultModel", () => {
  test("resolves speech model", () => {
    expect(resolveDefaultModel("chat.speech")).toBe(DEFAULT_MODEL.SPEECH);
  });

  test("resolves tts model", () => {
    expect(resolveDefaultModel("some.tts.feature")).toBe(DEFAULT_MODEL.SPEECH);
  });

  test("resolves video analysis to whisper", () => {
    expect(resolveDefaultModel("media.video.analysis")).toBe(DEFAULT_MODEL.AUDIO_TRANSCRIPTION);
  });

  test("resolves video model", () => {
    expect(resolveDefaultModel("media.video.generate")).toBe(DEFAULT_MODEL.VIDEO);
  });

  test("resolves audio model", () => {
    expect(resolveDefaultModel("media.audio")).toBe(DEFAULT_MODEL.AUDIO_TRANSCRIPTION);
  });

  test("resolves vision model", () => {
    expect(resolveDefaultModel("media.vision")).toBe(DEFAULT_MODEL.VISION);
  });

  test("falls back to IMAGE model", () => {
    expect(resolveDefaultModel("unknown")).toBe(DEFAULT_MODEL.IMAGE);
  });
});

// ── Constants integrity ──────────────────────────────────────────────────────

describe("Constants integrity", () => {
  test("CATEGORY_VALUES contains all categories", () => {
    expect(CATEGORY_VALUES).toContain("speech");
    expect(CATEGORY_VALUES).toContain("image");
    expect(CATEGORY_VALUES).toContain("video");
    expect(CATEGORY_VALUES).toContain("vision");
    expect(CATEGORY_VALUES).toContain("audio");
    expect(CATEGORY_VALUES).toHaveLength(5);
  });

  test("NODE_TO_CATEGORY maps all node IDs", () => {
    expect(NODE_TO_CATEGORY[NODE_ID.CHAT_SPEECH]).toBe(MODEL_CATEGORY.SPEECH);
    expect(NODE_TO_CATEGORY[NODE_ID.CHAT_IMAGE]).toBe(MODEL_CATEGORY.IMAGE);
    expect(NODE_TO_CATEGORY[NODE_ID.MEDIA_VIDEO]).toBe(MODEL_CATEGORY.VIDEO);
  });

  test("FALLBACK_MODELS has entries for all categories", () => {
    for (const cat of CATEGORY_VALUES) {
      expect(FALLBACK_MODELS[cat]).toBeDefined();
      expect(FALLBACK_MODELS[cat].length).toBeGreaterThan(0);
    }
  });

  test("COMMERCIAL_DEFAULTS has required providers", () => {
    expect(COMMERCIAL_DEFAULTS.gemini.default).toBeDefined();
    expect(COMMERCIAL_DEFAULTS.anthropic.default).toBeDefined();
    expect(COMMERCIAL_DEFAULTS.openai.default).toBeDefined();
  });

  test("DEFAULT_VOICE and DEFAULT_PROVIDER are defined", () => {
    expect(DEFAULT_VOICE).toBe("ryan");
    expect(DEFAULT_PROVIDER).toBe("ollama");
  });

  test("NODE_ICON constants are defined", () => {
    expect(NODE_ICON.IMAGE).toBe("image");
    expect(NODE_ICON.MIC).toBe("mic");
  });
});
