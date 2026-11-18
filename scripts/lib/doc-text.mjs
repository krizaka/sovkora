// Text helpers of the docs generator: Javadoc / XML text turned into Markdown-safe prose.
// Each one is written so that a single pass cannot leave behind what it meant to remove.

/** Removes HTML tags until none is left, so `<<b>script>` cannot rebuild a tag out of its own remains. */
export function stripTags(text) {
  let out = String(text);
  for (let previous = ""; previous !== out; ) {
    previous = out;
    out = out.replace(/<[^<>]+>/g, "");
  }
  return out;
}

/** A Markdown table cell: the backslash is escaped first, then the pipe. */
export function mdCell(text) {
  return String(text).replaceAll("\\", "\\\\").replaceAll("|", String.raw`\|`);
}

/** XML character references, `&amp;` last so `&amp;lt;` reads `&lt;`, not `<`. */
export function xmlDecode(text) {
  return String(text).replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&amp;", "&");
}
