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
    // As found (`::1`), and without the punctuation of the sentence around it (`192.0.2.4.` at the end of a comment).
    const found = [...raw.matchAll(CANDIDATE)].some(([run]) => isIP(run) !== 0 || isIP(run.replace(/^[:.]+|[:.]+$/g, "")) !== 0);
    return found ? [{ line: i + 1, key: keyOf(raw) }] : [];
  });
}

/** One sentence per literal address of `text`, naming the line and the key; empty when there is none. */
export function addressProblems(text) {
  return addressLiterals(text).map(
    ({ line, key }) => `line ${line}${key ? ` (${key})` : ""} writes a network address in clear: a SECRET placeholder filled at apply`,
  );
}
