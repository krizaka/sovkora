/**
 * @jest-environment node
 */
import { clips } from "@/core/context/translations.clips";
import { CLIPS, CLIP_IDS, LANGS, captionsVtt, clipBase, clipFiles } from "@/core/clips/clips";
import { GET } from "@/app/captions/[clip]/[lang]/route";

describe("the demo clips", () => {
  it.each(CLIP_IDS)("%s has its words in every language: alt, one-line caption, captions", (id) => {
    for (const lang of LANGS) {
      const copy = clips[lang][id];
      expect(copy.alt.length).toBeGreaterThan(10);
      expect(copy.caption.length).toBeLessThanOrEqual(80);
      expect(copy.cues.length).toBeGreaterThanOrEqual(2);
    }
    expect(clips.en[id].cues).toHaveLength(clips.fr[id].cues.length);
  });

  it.each(CLIP_IDS)("%s resolves to hashed CDN files in both themes", (id) => {
    for (const look of ["dark", "light"] as const) {
      const f = clipFiles(id, "fr", look);
      expect(f.webm).toMatch(/\.[0-9a-f]{10}\.webm$/);
      expect(f.mp4).toMatch(/\.[0-9a-f]{10}\.mp4$/);
      expect(f.poster).toMatch(/-poster\.[0-9a-f]{10}\.webp$/);
    }
  });

  it("only the chat take is recorded per language", () => {
    expect(CLIP_IDS.filter((id) => CLIPS[id].perLang)).toEqual(["take"]);
    expect(clipBase("take", "fr", "dark")).toBe("sites/orazaka/home/take-fr-dark");
    expect(clipBase("studio", "fr", "dark")).toBe(clipBase("studio", "en", "dark"));
  });

  it("the phone clips are the demo persona's framed 9:16 recordings of the app, one table, no second one", () => {
    expect(clipBase("phone-studio", "fr", "light")).toBe("sites/orazaka/mobile/studio-run-light-916");
    expect(clipBase("phone-ask", "en", "dark")).toBe("sites/orazaka/mobile/ask-dark-916");
    for (const id of ["phone-studio", "phone-ask"] as const) {
      expect(CLIPS[id].width / CLIPS[id].height).toBeCloseTo(9 / 16, 3);
      expect(CLIPS[id].perLang).toBe(false);
    }
  });

  it("writes WebVTT whose cues share the clip's length in order", () => {
    const vtt = captionsVtt("studio", ["a", "b"]);
    expect(vtt).toBe("WEBVTT\n\n00:00.000 --> 00:03.000\na\n\n00:03.000 --> 00:06.000\nb\n");
  });

  it("serves /captions/<clip>/<lang> as text/vtt, and 404s an unknown clip", async () => {
    const ok = await GET(new Request("http://x"), { params: Promise.resolve({ clip: "take", lang: "fr" }) });
    expect(ok.headers.get("content-type")).toMatch(/^text\/vtt/);
    expect(await ok.text()).toContain(clips.fr.take.cues[0]);
    const gone = await GET(new Request("http://x"), { params: Promise.resolve({ clip: "nope", lang: "en" }) });
    expect(gone.status).toBe(404);
  });
});
