import { test, expect } from "@playwright/test";

/**
 * Every link of the public home leads to a real place: no 4xx/5xx, no login wall for public content, and a hash
 * always names an element that exists on its target page. Walks the links of the sections that sell the product
 * (What it does · Proof) and the bar, in English and French.
 */
for (const locale of ["en", "fr"] as const) {
  test.describe(`home links (${locale})`, () => {
    test.use({
      locale: locale === "fr" ? "fr-FR" : "en-US",
      storageState: {
        cookies: [
          {
            name: "NEXT_LOCALE",
            value: locale,
            domain: "localhost",
            path: "/",
            expires: -1,
            httpOnly: false,
            secure: false,
            sameSite: "Lax",
          },
        ],
        origins: [],
      },
    });

    test("no link is broken, none lands on a login wall, every hash has its target", async ({ page }) => {
      test.setTimeout(240_000); // the dev server compiles each route on first visit
      await page.goto("/");
      const hrefs = await page.$$eval("header a[href], main a[href], footer a[href]", (as) => [
        ...new Set(as.map((a) => a.getAttribute("href") ?? "")),
      ]);
      const internal = hrefs.filter((h) => (h.startsWith("/") || h.startsWith("#")) && !h.startsWith("//"));
      expect(internal.length).toBeGreaterThan(15);

      const problems: string[] = [];
      for (const href of internal) {
        const target = href.startsWith("#") ? `/${href}` : href;
        const [path, hash] = target.split("#");
        const res = await page.goto(path || "/");
        if (!res || res.status() >= 400) problems.push(`${href}: HTTP ${res?.status()}`);
        if (href !== "/login" && new URL(page.url()).pathname === "/login")
          problems.push(`${href}: lands on the login wall`);
        if (hash && (await page.locator(`[id="${hash}"]`).count()) === 0) problems.push(`${href}: no element #${hash}`);
      }
      expect(problems).toEqual([]);
    });

    test("the proof is three run records, and its link leads to the measured runs", async ({ page }) => {
      await page.goto("/");
      const records = page.locator("#records").locator("xpath=ancestor::section[1]");
      await expect(records.locator("ul > li")).toHaveCount(3);
      await records.locator("a[href='/docs/measured-runs']").click();
      await expect(page).toHaveURL(/\/docs\/measured-runs$/, { timeout: 30_000 });
    });

    test("the bar reaches How it works, Packs and the Mac download", async ({ page }) => {
      await page.goto("/");
      for (const h of ["a[href='/docs/packs']", "a[href='/desktop']", "a[href$='#features']"]) {
        await expect(page.locator(`header ${h}`).first()).toBeVisible();
      }
    });
  });
}
