/**
 * @jest-environment node
 */
import fs from "node:fs";
import path from "node:path";
import { hasMedia } from "@/core/media/cdn";
import { landing } from "@/core/context/translations.landing";

const read = (p: string) => fs.readFileSync(path.join(process.cwd(), p), "utf8");
const FILES = [
  "montrer-son-travail.webm",
  "montrer-son-travail.mp4",
  "montrer-son-travail-poster.webp",
  "shot-duck.webm",
  "shot-duck.mp4",
  "shot-duck-poster.webp",
  "shot-ink-drop.webm",
  "shot-ink-drop.mp4",
  "shot-ink-drop-poster.webp",
  "keyframe-5.webp",
  "keyframe-2.webp",
];

describe("« Made with Sovkora »: the home film and the real outputs on the Studios card (krizaka-internal#138)", () => {
  it("serves every file from Sovkora's own site path on the CDN, hashed, never from pr-assets", () => {
    for (const file of FILES) {
      expect(hasMedia(`sites/orazaka/films/${file}`)).toBe(true);
    }
    for (const src of ["LandingFilm", "FilmPlayer", "StudioOutputs"]) {
      expect(read(`src/features/landing/components/${src}.tsx`)).not.toMatch(/pr-assets|b-cdn\.net/);
    }
  });

  it("sits right after the capabilities and before the measured records", () => {
    const page = read("src/features/landing/components/LandingPage.tsx");
    expect(page.indexOf("<LandingCapabilities />")).toBeLessThan(page.indexOf("<LandingFilm"));
    expect(page.indexOf("<LandingFilm")).toBeLessThan(page.indexOf("<LandingRecords"));
  });

  it("plays only on screen, keeps the poster under reduced motion and reserves its 16:9 box", () => {
    const player = read("src/features/landing/components/FilmPlayer.tsx");
    expect(player).toMatch(/useStillMotion/);
    expect(player).toMatch(/useOnScreen/);
    expect(player).toContain("aspect-video");
    expect(player).toContain("controls");
    expect(player).toMatch(/preload=\{still \? "none" : "metadata"\}/);
    const outputs = read("src/features/landing/components/StudioOutputs.tsx");
    expect(outputs).toContain("aspect-video");
    expect(outputs).toContain("motion-reduce:transition-none");
    expect(outputs).not.toMatch(/requestAnimationFrame/);
  });

  it.each(["en", "fr"] as const)("%s: the words claim only what the records hold", (locale) => {
    const f = landing[locale].film;
    const words = JSON.stringify(f);
    expect(words).not.toMatch(/4k|absolu|absolute|guarant|garanti|100 ?%|\bfree\b|gratuit/i);
    expect(f.costNote).toMatch(/key frames|images clés/);
    expect(Object.keys(f.outputs).sort()).toEqual(Object.keys(landing.en.film.outputs).sort());
    expect(f.outputs.videoCaption).toContain("{run}");
    expect(f.outputs.videoCaption).toContain("{model}");
  });
});
