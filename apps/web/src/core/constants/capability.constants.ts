/**
 * Central registry of capability node identifiers, media kind classifiers,
 * default model names, and icon tokens used across the playground and
 * chat-session features.
 *
 * <p>Any magic string that identifies a capability, model, or media kind
 * MUST be declared here and imported by consuming modules. Inline string
 * literals for these concepts are strictly prohibited.
 *
 * @see useNodeCardState
 * @see ChatWindow
 * @see NodeFieldRenderer
 * @see NodeHints
 */

// ─── Node Identifiers ───────────────────────────────────────────────────────
// Canonical namespace-qualified IDs as emitted by the Operation Graph backend.

export const NODE_ID = {
  CHAT_SPEECH: "orazaka.core.media.speech",
  CHAT_IMAGE: "orazaka.core.chat.image",
  MEDIA_VIDEO: "orazaka.core.media.video",
  MEDIA_VISION: "orazaka.core.media.vision",
  MEDIA_AUDIO: "orazaka.core.media.audio.analysis",
} as const;

// ─── Model Categories ────────────────────────────────────────────────────────
// Catalog categories used to filter available models per capability.

export const MODEL_CATEGORY = {
  SPEECH: "speech",
  IMAGE: "image",
  VIDEO: "video",
  VISION: "vision",
  AUDIO: "audio",
} as const;

/** Union type derived from MODEL_CATEGORY values. */
export type ModelCategoryValue = (typeof MODEL_CATEGORY)[keyof typeof MODEL_CATEGORY];

/** All category values as an iterable array — use in admin dropdowns and filters. */
export const CATEGORY_VALUES: readonly ModelCategoryValue[] = Object.values(MODEL_CATEGORY) as ModelCategoryValue[];

/** Admin-specific categories extending MODEL_CATEGORY with theme/code. */
export const CATEGORIES = [...CATEGORY_VALUES, "theme", "code"] as const;

// ─── Feature Key → Provider Mapping ──────────────────────────────────────────

const PROVIDER_RULES: readonly { keyword: string; provider: string }[] = [
  { keyword: MODEL_CATEGORY.VIDEO, provider: "localai-video" },
  { keyword: MODEL_CATEGORY.IMAGE, provider: "localai-image" },
  { keyword: MODEL_CATEGORY.SPEECH, provider: "localai" },
  { keyword: MODEL_CATEGORY.AUDIO, provider: "localai" },
];

/**
 * Resolves the provider name from a feature key string.
 * Replaces scattered provider-resolution if/else chains.
 */
export function resolveProviderFromFeature(featureKey: string): string {
  const lower = featureKey.toLowerCase();
  const match = PROVIDER_RULES.find((r) => lower.includes(r.keyword));
  return match?.provider ?? "localai";
}

// ─── Node ID → Model Category mapping ────────────────────────────────────────

export const NODE_TO_CATEGORY: Record<string, string> = {
  [NODE_ID.CHAT_SPEECH]: MODEL_CATEGORY.SPEECH,
  [NODE_ID.CHAT_IMAGE]: MODEL_CATEGORY.IMAGE,
  [NODE_ID.MEDIA_VIDEO]: MODEL_CATEGORY.VIDEO,
  [NODE_ID.MEDIA_VISION]: MODEL_CATEGORY.VISION,
  [NODE_ID.MEDIA_AUDIO]: MODEL_CATEGORY.AUDIO,
};

/** Capability feature-key → catalog model category (analysis-aware, substring match).
 *  Order matters: the more specific tokens (vision/video/speech) precede generic audio/image.
 *  Returns null for capabilities with no model choice (e.g. plain chat). */
const CATEGORY_RESOLUTION_RULES: readonly {
  keyword: string;
  category: ModelCategoryValue;
}[] = [
  { keyword: MODEL_CATEGORY.VISION, category: MODEL_CATEGORY.VISION },
  { keyword: MODEL_CATEGORY.VIDEO, category: MODEL_CATEGORY.VIDEO },
  { keyword: MODEL_CATEGORY.SPEECH, category: MODEL_CATEGORY.SPEECH },
  { keyword: MODEL_CATEGORY.AUDIO, category: MODEL_CATEGORY.AUDIO },
  { keyword: MODEL_CATEGORY.IMAGE, category: MODEL_CATEGORY.IMAGE },
];

/** Resolves the model catalog category a capability draws its model list from. */
export function resolveModelCategory(featureId: string): ModelCategoryValue | null {
  const lower = featureId.toLowerCase();
  return CATEGORY_RESOLUTION_RULES.find((r) => lower.includes(r.keyword))?.category ?? null;
}

// ─── Media Kind ──────────────────────────────────────────────────────────────
// Discriminators for how a capability result should be rendered.

// "run-pending" is a transient placeholder for a run the composer started: it renders
// a "generating" indicator + a link to the run, then the reconciler swaps the message
// to what the run produced (image/video/audio/text) once the run reaches a terminal
// state. It carries a run rather than a job since ADR-068 — the job a step dispatches
// is the run's business, not the chat's.
export type MediaKind = "image" | "audio" | "video" | "text" | "run-pending";

/** Keyword tokens that indicate a video-type capability. */
const VIDEO_KEYWORDS = ["video"] as const;

/** Keyword tokens that indicate an image-type capability. */
const IMAGE_KEYWORDS = ["image", "vision"] as const;

/** Keyword tokens that indicate an audio-type capability. */
const AUDIO_KEYWORDS = ["audio", "speech", "tts"] as const;

/** Icon tokens mapped to media kinds. */
const ICON_KIND_MAP: Record<string, MediaKind> = {
  image: "image",
  mic: "audio",
  video: "video",
};

/**
 * Resolves the media kind from a capability identifier and optional icon token.
 *
 * @param id - The lowercase capability identifier string.
 * @param icon - Optional icon token from the operation node schema.
 * @returns The resolved media kind: "video", "image", "audio", or "text".
 */
export function resolveMediaKind(id: string, icon?: string): MediaKind {
  const lower = id.toLowerCase();

  if (VIDEO_KEYWORDS.some((kw) => lower.includes(kw))) return "video";
  if (IMAGE_KEYWORDS.some((kw) => lower.includes(kw))) return "image";
  if (AUDIO_KEYWORDS.some((kw) => lower.includes(kw))) return "audio";
  if (icon && icon in ICON_KIND_MAP) return ICON_KIND_MAP[icon];

  return "text";
}

// ─── Default Models ──────────────────────────────────────────────────────────
// Fallback model identifiers used when the catalog is unavailable.

export const DEFAULT_MODEL = {
  IMAGE: "sdxl-turbo-gguf",
  SPEECH: "piper-en-medium-ryan",
  VIDEO: "stable-video-diffusion-img2vid-xt",
  AUDIO_TRANSCRIPTION: "whisper-base",
  VISION: "llama3.2-vision:latest",
} as const;

/** Keyword tokens used to resolve the default model from a feature ID. */
const MODEL_RESOLUTION_RULES: {
  keywords: readonly string[];
  model: string;
}[] = [
  { keywords: ["speech", "tts"], model: DEFAULT_MODEL.SPEECH },
  {
    keywords: ["video.analysis", "video_analysis"],
    model: DEFAULT_MODEL.AUDIO_TRANSCRIPTION,
  },
  { keywords: ["video"], model: DEFAULT_MODEL.VIDEO },
  { keywords: ["audio"], model: DEFAULT_MODEL.AUDIO_TRANSCRIPTION },
  { keywords: ["vision"], model: DEFAULT_MODEL.VISION },
];

/**
 * Resolves the default model identifier from a feature/capability ID.
 * Rules are evaluated in priority order — first match wins.
 *
 * @param featureId - The capability identifier string.
 * @returns The resolved default model name.
 */
export function resolveDefaultModel(featureId: string): string {
  const lower = featureId.toLowerCase();
  for (const rule of MODEL_RESOLUTION_RULES) {
    if (rule.keywords.some((kw) => lower.includes(kw))) {
      return rule.model;
    }
  }
  return DEFAULT_MODEL.IMAGE;
}

// ─── Default Voice ───────────────────────────────────────────────────────────

export const DEFAULT_VOICE = "ryan";

// ─── Capability option choices (chat composer dropdowns) ─────────────────────
// Selectable values surfaced per capability. Declared here as the single source
// so no option literal is inlined in components.

export const SPEECH_VOICES = ["alloy", "echo", "onyx", "nova", "shimmer", "ryan"] as const;
export const IMAGE_SIZES = ["512x512", "768x768", "1024x1024", "1024x1792"] as const;
export const VIDEO_DURATIONS = [3, 5, 8, 10] as const;

// ─── Default Provider ────────────────────────────────────────────────────────

export const DEFAULT_PROVIDER = "ollama";

// ─── Fallback Model Catalogs ─────────────────────────────────────────────────
// Used when the backend catalog is unavailable. Keyed by MODEL_CATEGORY.

export const FALLBACK_MODELS: Record<string, readonly string[]> = {
  [MODEL_CATEGORY.SPEECH]: ["piper-en-low", DEFAULT_MODEL.SPEECH, "piper-fr-medium", "tts-1"],
  [MODEL_CATEGORY.IMAGE]: [DEFAULT_MODEL.IMAGE, "sd-1.5-apple-coreml", "stable-diffusion-xl"],
  [MODEL_CATEGORY.VIDEO]: [DEFAULT_MODEL.VIDEO, "animatediff-lightning-mps", "apple-coreml-video-pipeline"],
  [MODEL_CATEGORY.VISION]: ["llava:latest", "llava:v1.6", "bakllava:latest", DEFAULT_MODEL.VISION],
  [MODEL_CATEGORY.AUDIO]: [DEFAULT_MODEL.AUDIO_TRANSCRIPTION, "whisper-tiny-en"],
};

// ─── Commercial Provider Defaults ────────────────────────────────────────────
// Default model per commercial provider when the catalog has no entries.

export const COMMERCIAL_DEFAULTS: Record<string, Partial<Record<string, string>> & { default: string }> = {
  gemini: { default: "gemini-1.5-flash" },
  anthropic: { default: "claude-3-5-sonnet-latest" },
  openai: {
    [MODEL_CATEGORY.SPEECH]: "tts-1",
    [MODEL_CATEGORY.IMAGE]: "dall-e-3",
    default: "gpt-4o",
  },
};

// ─── Icon Tokens ─────────────────────────────────────────────────────────────

export const NODE_ICON = {
  IMAGE: "image",
  MIC: "mic",
} as const;
