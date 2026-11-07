import fs from "node:fs";
import path from "node:path";
import { PRIVACY_CONTENT, TERMS_CONTENT } from "@/features/landing/utils/legal-content";

/** krizaka/orazaka#443: privacy and terms say it plainly — no internal jargon, sentence case, a title of their own. */
const JARGON = /invariant|telemetry|bridges|bare-metal|zéro|passerelles|prédictions/i;
const CONTENT = { privacy: PRIVACY_CONTENT, terms: TERMS_CONTENT };

describe.each(["privacy", "terms", "login"] as const)("/%s", (route) => {
  const dir = path.join(__dirname, "..", route);
  it("has its own page title", () => {
    expect(fs.readFileSync(path.join(dir, "layout.tsx"), "utf8")).toMatch(/title: "[^"]+ — Sovkora"/);
  });
  if (route !== "login") {
    it("uses no internal jargon in its headings, and no Title Case, in either language", () => {
      const content = CONTENT[route];
      const titles = [content.en, content.fr].flatMap((c) => [c.title, ...c.sections.map((s) => s.title)]);
      expect(titles.length).toBeGreaterThan(4);
      for (const t of titles) {
        expect(t).not.toMatch(JARGON);
        expect(t).not.toMatch(/^(\d\. )?[A-Z]\w*( [A-Z]\w*){2,}/);
      }
    });
  }
});
