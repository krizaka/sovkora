// One `KEY=value` line of a `.env` file, read in linear time: the separators around the `=` and the value cannot overlap.

/** `{ key, value }` of an upper-case `KEY=value` line (surrounding quotes dropped), or null for a comment, a blank or any other line. */
export function parseDotEnvLine(line) {
  const match = /^\s*([A-Z0-9_]+)\s*=(.*)$/.exec(line);
  if (!match) return null;
  return { key: match[1], value: match[2].trim().replace(/^["']|["']$/g, "") };
}
