import { translations } from "@/core/context/translations";
import { APPEARANCES, STEPS, clipUrls } from "@/core/mobile/mobile";
import { mediaPaths } from "@/core/media/cdn";

const CDN = /^https:\/\/krizaka-assets\.b-cdn\.net\/sites\/orazaka\/mobile\/[a-z0-9-]+\.[0-9a-f]{10}\.(webm|mp4|webp)$/;

describe("the mobile journey", () => {
  it("is a few clear steps, in the order a person lives them", () => {
    expect(STEPS).toEqual(["sign-in", "studio-run", "ask", "jobs"]);
  });

  it("points every step at the CDN under sites/orazaka/mobile, with a content hash, in both encodings and a poster, in dark and in light", () => {
    for (const id of STEPS) {
      for (const appearance of APPEARANCES) {
        const u = clipUrls(id, appearance);
        expect([u.webm, u.mp4, u.poster].every((x) => CDN.test(x))).toBe(true);
        expect(u.mp4).toContain(`/${id}-${appearance}-916.`);
      }
    }
  });

  it("shows only the persona's recordings of the real app (Mira, framed, 9:16): never a still or promo, never the older QA-user takes", () => {
    const shown = STEPS.flatMap((id) => APPEARANCES.flatMap((a) => Object.values(clipUrls(id, a))));
    expect(shown.filter((u) => /\/clip-/.test(u))).toEqual([]);
    const known = mediaPaths("sites/orazaka/mobile/");
    for (const id of STEPS)
      for (const a of APPEARANCES) expect(known).toContain(`sites/orazaka/mobile/${id}-${a}-916.mp4`);
  });

  it("has a goal, a purpose, an alt and three things to notice for every step, in English and French", () => {
    for (const locale of ["en", "fr"] as const) {
      const m = translations[locale].mobile;
      expect(Object.keys(m.steps).sort()).toEqual([...STEPS].sort());
      for (const id of STEPS) {
        const s = m.steps[id];
        for (const text of [s.goal, s.short, s.purpose, s.alt]) expect(text.trim()).not.toBe("");
        expect(s.notice).toHaveLength(3);
        s.notice.forEach((n) => expect(n.trim()).not.toBe(""));
      }
    }
    for (const id of STEPS) {
      expect(translations.fr.mobile.steps[id].goal).not.toBe(translations.en.mobile.steps[id].goal);
      expect(translations.fr.mobile.steps[id].purpose).not.toBe(translations.en.mobile.steps[id].purpose);
    }
  });
});
