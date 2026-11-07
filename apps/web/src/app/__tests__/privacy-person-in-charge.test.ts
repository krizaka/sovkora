import { PRIVACY_CONTENT } from "@/features/landing/utils/legal-content";

// Law 25 (Québec), founder decision 2026-11-05: the person in charge is published by role, never by name.
const LINE = {
  en: "Person in charge of the protection of personal information: the president of Technologies Krizaka Inc. — confidentialite@krizaka.com",
  fr: "Responsable de la protection des renseignements personnels : le président de Technologies Krizaka Inc. — confidentialite@krizaka.com",
} as const;

describe("the privacy policy names the person in charge (Law 25)", () => {
  for (const locale of ["en", "fr"] as const) {
    it(`${locale}: the president's role and confidentialite@krizaka.com, no person's name`, () => {
      const c = PRIVACY_CONTENT[locale];
      expect(c.sections.map((s) => s.text)).toContain(LINE[locale]);
      expect(JSON.stringify(c)).not.toMatch(/Oussama|Abid|contact@krizaka/i);
    });

    it(`${locale}: the policy is dated after the June 2026 text`, () => {
      expect(PRIVACY_CONTENT[locale].lastUpdated).toMatch(/November 5, 2026|5 novembre 2026/);
    });
  }
});
