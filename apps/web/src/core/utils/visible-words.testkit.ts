/** What a person reads in a rendered tree: its text, and the words a screen reader or a tooltip speaks (aria-label, title, alt). */
export function visibleWords(root: HTMLElement): string {
  const spoken = [...root.querySelectorAll("[aria-label],[title],[alt]")].flatMap((el) =>
    ["aria-label", "title", "alt"].map((name) => el.getAttribute(name) ?? ""),
  );
  return [root.textContent ?? "", ...spoken].join("\n");
}
