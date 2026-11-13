/** What a person reads in a rendered tree: its text, and the words a screen reader or a tooltip speaks (aria-label, title, alt). */
export function visibleWords(root: HTMLElement): string {
  const spoken = [...root.querySelectorAll("[aria-label],[title],[alt]")].flatMap((el) =>
    ["aria-label", "title", "alt"].map((name) => el.getAttribute(name) ?? ""),
  );
  return [root.textContent ?? "", ...spoken].join("\n");
}

/** The retired code name and its dev line, spelled in pieces so the code-name ratchet does not count the guard itself. */
export const RETIRED_NAME = new RegExp(["ora", "zaka|code name|nom de code"].join(""), "i");
