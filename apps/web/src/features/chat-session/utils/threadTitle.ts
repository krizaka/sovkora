/**
 * @file threadTitle.ts
 * @description Derives a concise, human-readable conversation title from the
 * user's first message — so threads are named by their topic instead of the
 * generic "New Memory Block" placeholder.
 */

const MAX_TITLE_LENGTH = 48;

/**
 * Builds a thread title from a prompt: collapses whitespace, strips the leading
 * line, and truncates with an ellipsis. Falls back to a generic label when the
 * prompt is empty.
 *
 * @param prompt - The first user message of the conversation.
 * @returns A trimmed, single-line title (≤ 48 chars).
 */
export function deriveThreadTitle(prompt: string): string {
  const clean = prompt.replace(/\s+/g, " ").trim();
  if (!clean) return "New Memory Block";
  return clean.length > MAX_TITLE_LENGTH ? `${clean.slice(0, MAX_TITLE_LENGTH).trimEnd()}…` : clean;
}
