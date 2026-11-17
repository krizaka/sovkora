// Where an app spec writes a network address in clear (krizaka-restricted#340). An allowed client address is a deploy
// secret, filled at apply from the secrets store like every other `__NAME__`: a literal in a public spec publishes whose
// connection it is. The finding names the line and the key, never the address, so a failing check repeats nothing.
import { isIP } from "node:net";

/** A run of the characters an IPv4 or IPv6 literal is made of; a `/prefix` after it is the same address. */
const CANDIDATE = /[0-9A-Fa-f:.]{3,}/g;

/** The `key:` of a flow-form env line, or null. */
const keyOf = (line) => /key: ([A-Z0-9_]+)/.exec(line)?.[1] ?? null;

/**
 * Every line of `text` that holds a literal IPv4 or IPv6 address, as `{ line, key }` (1-based line, the env key or null).
 * The address itself is never returned.
 */
export function addressLiterals(text) {
  return text.split("\n").flatMap((raw, i) => {
    const found = [...raw.matchAll(CANDIDATE)].some(([run]) => isIP(run) !== 0 || isIP(trimPunctuation(run)) !== 0);
    return found ? [{ line: i + 1, key: keyOf(raw) }] : [];
  });
}

/** A run without the `:` and `.` of the sentence around it (`192.0.2.4.` at the end of a comment); `::1` is tried as found. */
function trimPunctuation(run) {
  const edge = (c) => c === ":" || c === ".";
  let start = 0;
  let end = run.length;
  while (start < end && edge(run[start])) start++;
  while (end > start && edge(run[end - 1])) end--;
  return run.slice(start, end);
}

/** One sentence per literal address of `text`, naming the line and the key; empty when there is none. */
export function addressProblems(text) {
  return addressLiterals(text).map(({ line, key }) => {
    const where = key ? `line ${line} (${key})` : `line ${line}`;
    return `${where} writes a network address in clear: a SECRET placeholder filled at apply`;
  });
}
