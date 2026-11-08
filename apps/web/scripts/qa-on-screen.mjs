// The one rule « a demo shows a persona, never a QA name » (krizaka/.github#356, krizaka/orazaka#473): « qa_user », « qa-user »,
// « qa_ » or a lone « qa » in what a signed-in page shows. Shared by the UI crawl (a guard on every signed-in screen), the
// recordings and the takes, so there is one regex, not a copy per script.

/** True for « qa_user », « qa-user », « qa_… », « qa user » and a standalone « QA ». */
export const QA_ON_SCREEN = /\bqa[_\s-]|qa_user|\bqa\b/i;

/** What a person reads on the page: the visible text plus the accessible labels, titles and alternatives. Runs in the browser. */
export function readableText() {
  const attrs = [...document.querySelectorAll("[aria-label],[title],[alt]")].map((e) =>
    [e.getAttribute("aria-label"), e.getAttribute("title"), e.getAttribute("alt")].filter(Boolean).join(" "),
  );
  return `${document.body.innerText} ${attrs.join(" ")}`;
}

/** The first « qa » match on a Playwright page (null when clean). */
export async function qaOnScreen(page) {
  const text = await page.evaluate(readableText);
  return QA_ON_SCREEN.exec(text)?.[0] ?? null;
}
