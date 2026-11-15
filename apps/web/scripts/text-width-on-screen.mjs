// The one rule « a screen keeps its text readable » (krizaka-internal#3834): a paragraph or a heading is never squeezed into a
// sliver (« En cours… » one word per line). Shared by the Playwright specs and by every recording (record-tour, showcase-web,
// record-web-steps), so a squeezed text column fails the take instead of being published in a video.

/** A text column narrower than this (px) is squeezed; a block of words whose longest line holds this many words or fewer is too. */
export const MIN_COLUMN_PX = 280;
export const MAX_SQUEEZED_WORDS_PER_LINE = 3;
/** Below this many words a block is a label, not a column of reading text (a chip, a button). */
export const MIN_WORDS_OF_A_COLUMN = 6;

/** Runs in the browser: the visible text blocks that are squeezed (empty when every one reads well). Self-contained: it is serialised. */
export function squeezedBlocks(limits) {
  const { minColumn, maxWords, minWords } = limits;
  const countWords = (s) => s.split(/\s+/).filter(Boolean).length;

  /** Words per line: every word of the block's own text nodes, grouped by the line (top) it is drawn on. */
  const longestLine = (nodes) => {
    const lines = new Map();
    const addWord = (node, match) => {
      const range = document.createRange();
      range.setStart(node, match.index);
      range.setEnd(node, match.index + match[0].length);
      const rect = range.getClientRects()[0];
      if (rect) {
        lines.set(Math.round(rect.top), (lines.get(Math.round(rect.top)) ?? 0) + 1);
      }
    };
    for (const node of nodes) {
      [...(node.textContent ?? "").matchAll(/\S+/g)].forEach((match) => addWord(node, match));
    }
    return Math.max(0, ...lines.values());
  };

  const isDrawn = (el) => {
    const style = getComputedStyle(el);
    return style.display !== "inline" && style.display !== "none" && style.visibility !== "hidden";
  };

  /** A label cut to one line with an ellipsis (a thread title, a header) is shortened by design: it is not a column squeezed to a sliver. */
  const isEllipsizedLine = (el) => {
    const style = getComputedStyle(el);
    return style.textOverflow === "ellipsis" && style.whiteSpace === "nowrap";
  };

  const check = (el) => {
    const own = [...el.childNodes].filter((n) => n.nodeType === Node.TEXT_NODE);
    const text = own.map((n) => n.textContent ?? "").join(" ");
    const count = countWords(text);
    const isHeading = /^H[1-3]$/.test(el.tagName);
    const box = el.getBoundingClientRect();
    if (
      count < (isHeading ? 2 : minWords) ||
      !isDrawn(el) ||
      isEllipsizedLine(el) ||
      box.width === 0 ||
      box.height === 0
    ) {
      return null;
    }
    const longest = longestLine(own);
    // A list item is indented by its marker or icon column (the Stepper's): only its words per line are held, not its width.
    const narrow = box.width < minColumn && box.right <= window.innerWidth + 1 && !el.closest("li");
    const sliver = count >= minWords && longest <= maxWords;
    return narrow || sliver
      ? { text: text.trim().slice(0, 60), width: Math.round(box.width), longestLineWords: longest }
      : null;
  };

  const found = [...document.querySelectorAll("main p, main h1, main h2, main h3, main li, main span, main div")];
  return found.map(check).filter(Boolean);
}

/** The squeezed blocks of a Playwright page; the viewport narrower than the minimum is allowed its own width. */
export async function textSqueezedOnScreen(page) {
  const viewport = page.viewportSize();
  const minColumn = Math.min(MIN_COLUMN_PX, (viewport?.width ?? MIN_COLUMN_PX) - 2);
  return page.evaluate(squeezedBlocks, {
    minColumn,
    maxWords: MAX_SQUEEZED_WORDS_PER_LINE,
    minWords: MIN_WORDS_OF_A_COLUMN,
  });
}

/** Throws when a block of text on the page is squeezed: a recording or a spec of a screen calls it once the screen has settled. */
export async function assertTextReadable(page, label) {
  const squeezed = await textSqueezedOnScreen(page);
  if (squeezed.length > 0) {
    const first = squeezed[0];
    throw new Error(
      `${label}: the text is squeezed — « ${first.text} » is ${first.width}px wide with ${first.longestLineWords} word(s) on its longest line` +
        ` (at least ${MIN_COLUMN_PX}px and more than ${MAX_SQUEEZED_WORDS_PER_LINE} words wanted)`,
    );
  }
}
