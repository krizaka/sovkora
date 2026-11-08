import { test, expect } from "@playwright/test";

/** No horizontal overflow on the home, at every width that matters, in both themes and both languages. */
const WIDTHS = [360, 390, 430, 768, 1024, 1440];

for (const theme of ["dark", "light"] as const) {
  for (const locale of ["en", "fr"] as const) {
    test(`the home does not overflow sideways (${theme}, ${locale})`, async ({ page }) => {
      test.setTimeout(120_000);
      await page.addInitScript(
        ([t, l]) => {
          try {
            localStorage.setItem("kz-theme", t);
            localStorage.setItem("orazaka_language", l);
          } catch {}
        },
        [theme, locale],
      );
      const problems: string[] = [];
      for (const width of WIDTHS) {
        await page.setViewportSize({ width, height: 900 });
        await page.goto("/", { waitUntil: "networkidle" });
        const result = await page.evaluate(() => {
          const doc = document.documentElement;
          // A decorative layer (aria-hidden) or anything inside a clipping ancestor is meant to extend.
          const clipped = (el: Element) => {
            for (let n: Element | null = el; n && n !== document.body; n = n.parentElement) {
              if (n.getAttribute("aria-hidden") === "true") return true;
              if (n !== el && ["hidden", "clip", "auto", "scroll"].includes(getComputedStyle(n).overflowX)) return true;
            }
            return false;
          };
          const wide = [...document.querySelectorAll("body *")]
            .filter((el) => {
              const r = el.getBoundingClientRect();
              return r.width > 0 && (r.right > doc.clientWidth + 1 || r.left < -1) && !clipped(el);
            })
            .slice(0, 3)
            .map((el) => `${el.tagName.toLowerCase()}.${String(el.className).split(" ").slice(0, 3).join(".")}`);
          return { scroll: doc.scrollWidth, client: doc.clientWidth, wide };
        });
        if (result.scroll > result.client)
          problems.push(`@${width}: scrollWidth ${result.scroll} > ${result.client} (${result.wide.join(", ")})`);
        else if (result.wide.length) problems.push(`@${width}: elements past the edge (${result.wide.join(", ")})`);
      }
      expect(problems).toEqual([]);
    });
  }
}
