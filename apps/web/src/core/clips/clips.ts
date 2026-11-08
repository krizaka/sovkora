/**
 * @file clips.ts
 * @description The demo recordings of the public site: the home's chat take and the three web steps, all recorded on the
 * current UI as the demo persona (scripts/landing-take.mjs, scripts/record-web-steps.mjs) and served from the CDN
 * (`media.generated.json`). One vocabulary, one table: the home, the docs and the caption route read it.
 */
import { mediaUrl } from "@/core/media/cdn";

export const CLIP_IDS = ["take", "workspace", "studio", "results", "phone-studio", "phone-ask"] as const;
export type ClipId = (typeof CLIP_IDS)[number];

export const LANGS = ["en", "fr"] as const;
export type Lang = (typeof LANGS)[number];

export const LOOKS = ["dark", "light"] as const;
export type Look = (typeof LOOKS)[number];

interface ClipSpec {
  width: number;
  height: number;
  /** The box's ratio as a Tailwind class (spelled out: the scanner reads literals), so nothing shifts. */
  aspect: string;
  /** The shortest recording of the clip, in seconds: the captions are spread over it. */
  seconds: number;
  /** The take is recorded once per language (the question is typed in it); the web steps are the app as it is. */
  perLang: boolean;
  /** A clip recorded elsewhere than the web app (the phone app): its CDN path without look or extension. */
  base?: string;
}

export const CLIPS: Readonly<Record<ClipId, ClipSpec>> = {
  take: { width: 1140, height: 960, aspect: "aspect-[1140/960]", seconds: 8, perLang: true },
  workspace: { width: 1120, height: 700, aspect: "aspect-[1120/700]", seconds: 4, perLang: false },
  studio: { width: 1120, height: 700, aspect: "aspect-[1120/700]", seconds: 6, perLang: false },
  results: { width: 1120, height: 700, aspect: "aspect-[1120/700]", seconds: 5, perLang: false },
  // The phone app in a device frame, signed in as the demo persona (orazaka-mobile-client scripts/record-story.mjs, frame-clips.mjs).
  "phone-studio": {
    width: 1080,
    height: 1920,
    aspect: "aspect-[1080/1920]",
    seconds: 19,
    perLang: false,
    base: "sites/orazaka/mobile/studio-run",
  },
  "phone-ask": {
    width: 1080,
    height: 1920,
    aspect: "aspect-[1080/1920]",
    seconds: 11,
    perLang: false,
    base: "sites/orazaka/mobile/ask",
  },
};

export const isClipId = (id: string): id is ClipId => (CLIP_IDS as readonly string[]).includes(id);
export const isLang = (lang: string): lang is Lang => (LANGS as readonly string[]).includes(lang);

/** `sites/orazaka/home/take-fr-dark` or `sites/orazaka/web/step-studio-light`: the CDN path without extension. */
export function clipBase(id: ClipId, lang: Lang, look: Look): string {
  const { base } = CLIPS[id];
  if (base) return `${base}-${look}-916`;
  return CLIPS[id].perLang ? `sites/orazaka/home/${id}-${lang}-${look}` : `sites/orazaka/web/step-${id}-${look}`;
}

export function clipFiles(id: ClipId, lang: Lang, look: Look) {
  const base = clipBase(id, lang, look);
  return { webm: mediaUrl(`${base}.webm`), mp4: mediaUrl(`${base}.mp4`), poster: mediaUrl(`${base}-poster.webp`) };
}

const stamp = (s: number) => {
  const m = String(Math.floor(s / 60)).padStart(2, "0");
  return `${m}:${(s % 60).toFixed(3).padStart(6, "0")}`;
};

/** WebVTT for a clip: the cues share its length evenly. */
export function captionsVtt(id: ClipId, cues: readonly string[]): string {
  return cuesVtt(cues, CLIPS[id].seconds);
}

/** WebVTT for any recording of `seconds`: the cues share its length evenly (the app videos of the Download page use it too). */
export function cuesVtt(cues: readonly string[], seconds: number): string {
  const step = seconds / cues.length;
  const body = cues.map((text, i) => `${stamp(i * step)} --> ${stamp((i + 1) * step)}\n${text}`);
  return `WEBVTT\n\n${body.join("\n\n")}\n`;
}
