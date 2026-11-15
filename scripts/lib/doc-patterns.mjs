// Linear-time patterns of the docs generator (SonarCloud S8786). Each one reads source text of
// this repository; none lets two adjacent quantifiers match the same characters, so a long
// hostile line cannot make a scan super-linear. Whitespace that must not cross a line is
// `[^\S\n]` (a space or tab, never a newline).

/** A gateway route in `application.yaml`: path-prefix, then the `target: ${KEY:http://host:port}` line. */
export const GATEWAY_ROUTE = /-[^\S\n]*path-prefix:[^\S\n]*(\S+)[^\S\n]*\n\s*target:\s*\$\{[\w.-]+:https?:\/\/[\w.-]+:(\d+)\}/g;

/** A `static String name(String arg) { return expr; }` key builder: name, expression. */
export const KEY_BUILDER = /static\s+String\s+(\w+)\(\s*String\s+\w+\s*\)\s*\{\s*return\s+([^;\s][^;]*);/g;

/** The queue argument of a `@RabbitListener(queues = ...)`: a `{...}` list or one expression (untrimmed). */
export const RABBIT_LISTENER = /@RabbitListener\(\s*queues\s*=\s*(\{[^}]*\}\s*|[^,)\s][^,)]*)[,)]/g;

/** `convertAndSend(exchange, routingKey,`: both captures are untrimmed. */
export const CONVERT_AND_SEND = /convertAndSend\(([^,()]+),([^,]+),/g;

/** A `- "job.x"` binding line of a worker.yaml. */
export const WORKER_BINDING = /^[ \t]*-[ \t]*"(job\.[^"]+)"/gm;

/** A `{name}` placeholder (no nesting). */
export const BRACE_PLACEHOLDER = /\{[^{}]+\}/g;
/** A `{...}` path variable, possibly empty. */
export const PATH_VARIABLE = /\{[^{}]*\}/g;

/** The first line of a Javadoc block. */
export const FIRST_JAVADOC_LINE = /\/\*\*[^\S\n]*\n\s*\*\s*(\S[^\n]*)/;

/** An inline Javadoc tag, `{@code X}` / `{@link X}`: its text is the capture. */
export const JAVADOC_INLINE_TAG = /\{@\w+\s+([^}\s][^}]*)\}/g;

/** A comment-only line of Java source. */
export const COMMENT_LINE = /^[ \t]*(?:\/\/|\*|\/\*).*$/gm;

/** A `logStep("Phase X ...")` / `logStep("Workspace ...")` call: the whole text. */
export const LOG_STEP = /logStep\(\s*"((?:Phase\s+\w|Workspace)[^"]*)"\s*\)/g;

/** A port line of the CLI config: `name: intEnv(KEY, 8080)`. */
export const CLI_PORT = /^[ \t]+(\w+):[ \t]*intEnv\([^,]+,\s*(\d+)\)/gm;

/** A `@param name text` Javadoc line. */
export const PARAM_TAG = /^@param\s+(<?\w+>?)(?:\s|$)/;

/** A `${property:default}` reference. */
export const PROPERTY_REF = /^\$\{([^:}]+)(?::([^}]*))?\}$/;

/** The number that ends a schedule text, or is followed by ` (`. */
export const TRAILING_NUMBER = /(?<!\d)(\d+)(?= \(|$)/;

/** A YAML `key:` line (quoted or bare key), then the optional inline value. */
export const YAML_KEY_LINE = /^("[^"]+"|'[^']+'|[^:]+):(?:\s+(\S.*)?)?$/;

/** Splits `"a" + b + "c"` into its terms. */
export function splitConcatenation(token) {
  return token.split("+").map((part) => part.trim());
}

/** The `Phase X` / `Workspace` log steps of a CLI source: text, phase id (if any), offset. */
export function logSteps(code) {
  return [...code.matchAll(LOG_STEP)].map((m) => ({ text: m[1], phase: /^Phase\s+(\w+)/.exec(m[1])?.[1], index: m.index }));
}

/** A log step's text split into head and detail at ` — ` or `: `. */
export function splitStepText(text) {
  return text.split(/ — |: /);
}

/** The cleaned parameter list entry of a Java signature: its type and its name. */
export function paramOf(raw) {
  const p = raw.trim().split(/\s+/).join(" ");
  let start = p.length;
  while (start > 0 && /\w/.test(p[start - 1])) start--;
  const word = p.slice(start);
  const named = start > 0 && start < p.length && p[start - 1] === " ";
  return { type: named ? p.slice(0, start - 1) : p, name: word || undefined };
}

/** Source with its block comments removed (an unterminated one is kept). */
export function stripBlockComments(src) {
  let out = "";
  let from = 0;
  for (;;) {
    const open = src.indexOf("/*", from);
    if (open < 0) break;
    const close = src.indexOf("*/", open + 2);
    if (close < 0) break;
    out += src.slice(from, open);
    from = close + 2;
  }
  return out + src.slice(from);
}
