/**
 * @file markdown.ts
 * @description A small Markdown reader for what a model answers: headings, paragraphs, lists, quotes, code, rules and
 * the inline marks (bold, italic, code, links). It returns plain data, so a screen can draw it natively — React Native
 * has no HTML to hand it to, and a literal `**Nom :**` on a phone is the bug this exists to remove.
 *
 * Not a CommonMark engine: tables and HTML are left as text on purpose; the answer stays readable, never wrong.
 */

export type Inline =
  | { readonly kind: "text"; readonly text: string }
  | { readonly kind: "bold" | "italic" | "code"; readonly text: string }
  | { readonly kind: "link"; readonly text: string; readonly href: string };

export type Block =
  | { readonly kind: "heading"; readonly level: 1 | 2 | 3; readonly inline: readonly Inline[] }
  | { readonly kind: "paragraph"; readonly inline: readonly Inline[] }
  | { readonly kind: "list"; readonly ordered: boolean; readonly items: readonly (readonly Inline[])[] }
  | { readonly kind: "quote"; readonly inline: readonly Inline[] }
  | { readonly kind: "code"; readonly text: string }
  | { readonly kind: "rule" };

const INLINE_FORMS = [
  String.raw`\*\*[^*]+\*\*`,
  "__[^_]+__",
  String.raw`\*[^*\s][^*]*\*`,
  String.raw`_[^_\s][^_]*_`,
  "`[^`]+`",
  String.raw`\[[^\]]+\]\([^)\s]+\)`,
];
const INLINE = new RegExp(`(${INLINE_FORMS.join("|")})`, "g");

/** Reads the inline marks of one line of text. */
export function parseInline(source: string): Inline[] {
  const out: Inline[] = [];
  let last = 0;
  for (const match of source.matchAll(INLINE)) {
    const token = match[0];
    const at = match.index ?? 0;
    if (at > last) out.push({ kind: "text", text: source.slice(last, at) });
    if (token.startsWith("**") || token.startsWith("__")) {
      out.push({ kind: "bold", text: token.slice(2, -2) });
    } else if (token.startsWith("`")) {
      out.push({ kind: "code", text: token.slice(1, -1) });
    } else if (token.startsWith("[")) {
      const close = token.indexOf("](");
      out.push({ kind: "link", text: token.slice(1, close), href: token.slice(close + 2, -1) });
    } else {
      out.push({ kind: "italic", text: token.slice(1, -1) });
    }
    last = at + token.length;
  }
  if (last < source.length) out.push({ kind: "text", text: source.slice(last) });
  return out;
}

const BULLET = /^\s*[-*+]\s(.*)$/;
const NUMBERED = /^\s*\d+[.)]\s(.*)$/;
const HEADING = /^(#{1,6})\s(.*)$/;

type Heading = Extract<Block, { kind: "heading" }>;

/** Drops the closing `#` run of a heading, scanning from the end once. */
function stripTrailingHashes(text: string): string {
  let end = text.length;
  while (end > 0 && text[end - 1] === "#") end -= 1;
  return text.slice(0, end);
}

/** `## Title ##` → a heading of level 2 (at most 3) whose closing hashes are dropped. */
function readHeading(line: string): Heading | null {
  const match = HEADING.exec(line);
  if (!match) return null;
  const text = stripTrailingHashes((match[2] ?? "").trim()).trim();
  return { kind: "heading", level: Math.min(match[1]?.length ?? 1, 3) as 1 | 2 | 3, inline: parseInline(text) };
}

/** The list item a line opens: its text and whether the list is numbered. */
function readItem(line: string): { entry: Inline[]; ordered: boolean } | null {
  const bullet = BULLET.exec(line);
  const numbered = bullet ? null : NUMBERED.exec(line);
  const item = bullet ?? numbered;
  return item ? { entry: parseInline((item[1] ?? "").trimStart()), ordered: numbered !== null } : null;
}

/** A fenced block opened at `start`: its code and the index of the closing fence (or the end). */
function readFence(lines: readonly string[], start: number): { text: string; end: number } {
  const code: string[] = [];
  let i = start + 1;
  while (i < lines.length && !(lines[i] ?? "").trim().startsWith("```")) {
    code.push(lines[i] ?? "");
    i += 1;
  }
  return { text: code.join("\n"), end: i };
}

function addItem(blocks: Block[], item: { entry: Inline[]; ordered: boolean }): void {
  const previous = blocks.at(-1);
  if (previous?.kind === "list" && previous.ordered === item.ordered) {
    blocks[blocks.length - 1] = { ...previous, items: [...previous.items, item.entry] };
  } else {
    blocks.push({ kind: "list", ordered: item.ordered, items: [item.entry] });
  }
}

/** Reads a whole answer into blocks. A streamed, half-written answer is fine: an open fence is code to the end. */
export function parseMarkdown(source: string): Block[] {
  const lines = source.replace(/\r\n?/g, "\n").split("\n");
  const blocks: Block[] = [];
  let paragraph: string[] = [];

  const flush = () => {
    if (paragraph.length > 0) {
      blocks.push({ kind: "paragraph", inline: parseInline(paragraph.join(" ")) });
      paragraph = [];
    }
  };

  let i = 0;
  while (i < lines.length) {
    const line = lines[i] ?? "";
    const heading = readHeading(line);
    const item = readItem(line);
    if (line.trim().startsWith("```")) {
      flush();
      const fence = readFence(lines, i);
      blocks.push({ kind: "code", text: fence.text });
      i = fence.end;
    } else if (line.trim() === "") {
      flush();
    } else if (/^\s*([-*_])(\s*\1){2,}\s*$/.test(line)) {
      flush();
      blocks.push({ kind: "rule" });
    } else if (heading) {
      flush();
      blocks.push(heading);
    } else if (line.startsWith(">")) {
      flush();
      blocks.push({ kind: "quote", inline: parseInline(line.replace(/^>\s?/, "")) });
    } else if (item) {
      flush();
      addItem(blocks, item);
    } else {
      paragraph.push(line.trim());
    }
    i += 1;
  }
  flush();
  return blocks;
}
