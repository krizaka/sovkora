// A port of the engine's deterministic citation verifier (orazaka-ai-engine: CitationVerificationService, QuoteNormalizer, ReferenceScanner; ADR-087 §3), so a bake-off scores a model
// with the rule the engine will apply to it. Keep it in step with the Java: scripts/test/bakeoff.test.mjs holds the cases both must agree on.
import { createHash } from "node:crypto";

export const sha256 = (text) => createHash("sha256").update(text, "utf8").digest("hex");

const SPACES = /[\s     -    　]+/gu;
const DOUBLE_QUOTES = /[«»“”„‟″]/gu;
const SINGLE_QUOTES = /[‘’‚‛′`´]/gu;
const DASHES = /[‐‑‒–—―−]/gu;

/** Brings a quote and a text to one form: NFC, case, French typography, spaces (QuoteNormalizer.normalize). */
export function normalize(text) {
  if (text == null) return "";
  let s = String(text).normalize("NFC").toLowerCase();
  s = s.replaceAll("œ", "oe").replaceAll("æ", "ae").replaceAll("…", "...");
  s = s.replace(DOUBLE_QUOTES, '"').replace(SINGLE_QUOTES, "'").replace(DASHES, "-").replace(SPACES, " ");
  s = s.replaceAll('" ', '"').replaceAll(' "', '"');
  return s.trim();
}

export function containsQuote(text, quote) {
  const q = normalize(quote);
  return q !== "" && normalize(text).includes(q);
}

const NUMBERS = /\d+(?:[.,:/-]\d+)*/gu;
const SECTION_LABEL = /^(?:art(?:icle)?s?\.?|section|§|chapitre|chapter|partie|part|titre|title)\s*\d+[\w-]*(?:\.\w[\w-]*)*/iu;
const HEADING_MAX_CHARS = 120;

const URL_RE = /https?:\/\/[^\s)\]>"'«»]+/gu;
const REPOSITORY = /(?<![\w./-])([A-Za-z0-9][A-Za-z0-9-]{0,38})\/([A-Za-z0-9._-]{1,100})(?![\w/])/gu;
const STATUTE = /\b(?:art(?:icle)?s?\.?\s*\d[\w.-]*|RLRQ\s*,?\s*c(?:h)?\.?\s*[A-Z]-?[\d.]+|L\.?R\.?[CQ]\.?\s*\d{4}|R\.?S\.?C\.?\s*\d{4})/giu;

const trimPunctuation = (s) => {
  let end = s.length;
  while (end > 0 && ".,;:!?".includes(s[end - 1])) end--;
  return s.slice(0, end);
};

/** URLs, repositories and statute references stated in a text (ReferenceScanner.references). */
export function references(text) {
  const found = new Set();
  if (text == null) return found;
  for (const m of text.matchAll(URL_RE)) found.add(trimPunctuation(m[0]));
  const withoutUrls = text.replace(URL_RE, " ");
  for (const m of withoutUrls.matchAll(REPOSITORY)) {
    const candidate = trimPunctuation(m[0]);
    if (!/^\d+\/\d+$/u.test(candidate)) found.add(candidate);
  }
  for (const m of withoutUrls.matchAll(STATUTE)) found.add(trimPunctuation(m[0]).replace(/\s+/gu, " "));
  return found;
}

const isStatute = (ref) => {
  const m = ref.match(new RegExp(STATUTE.source, "iu"));
  return m !== null && m.index === 0 && m[0].length === ref.length;
};

/** The identity of a reference (ReferenceScanner.key). */
export function key(reference) {
  const ref = reference.trim();
  if (ref.startsWith("http://") || ref.startsWith("https://")) return ref;
  if (isStatute(ref)) {
    return `statute:${ref.toLowerCase().replace(/^art(?:icle)?s?/u, "art").replace(/[\s,]+/gu, "").replace(/(?<=[a-z])\./gu, "")}`;
  }
  return ref.toLowerCase();
}

export const keys = (text) => new Set([...references(text)].map(key));

const numbers = (text) => new Set(text.match(NUMBERS) ?? []);

/** The heading of the section a quote is in (CitationVerificationService.sectionHeading). */
export function sectionHeading(text, quote) {
  const paragraphs = (text ?? "").split(/\n[ \t]*\n/u);
  const normalised = paragraphs.map(normalize);
  const joined = normalised.join(" ");
  const needle = normalize(quote);
  const at = needle === "" ? -1 : joined.indexOf(needle);
  if (at < 0) return "";
  let index = 0;
  let offset = 0;
  while (index < normalised.length - 1 && offset + normalised[index].length < at) {
    offset += normalised[index].length + 1;
    index++;
  }
  const label = paragraphs[index].trim().match(SECTION_LABEL);
  if (label) return label[0];
  for (let i = index - 1; i >= 0; i--) {
    const above = paragraphs[i].trim();
    if (above !== "" && above.length <= HEADING_MAX_CHARS && !/[.!?;:]$/u.test(above)) return above;
  }
  return "";
}

function supported(claimText, citation, doc) {
  const heading = sectionHeading(doc.text, citation.quote);
  const scope = `${citation.quote} ${citation.url} ${doc.title} ${heading}`;
  const have = numbers(normalize(scope));
  for (const n of numbers(normalize(claimText))) if (!have.has(n)) return false;
  const haveKeys = keys(scope);
  for (const k of keys(claimText)) if (!haveKeys.has(k)) return false;
  return true;
}

/**
 * Verifies one claim against the documents read: provenance (url and hash name a document read, the quote is in it, at most maxQuote characters), then support.
 * Returns { status: "VERIFIED" | "TO_VALIDATE", reason, passing } where `passing` are the citations that passed both.
 */
export function verifyClaim(claim, read, maxQuote = 300) {
  const citations = Array.isArray(claim.citations) ? claim.citations : [];
  if (citations.length === 0) return { status: "TO_VALIDATE", reason: "NO_SOURCE", passing: [] };
  const order = ["URL_UNRESOLVED", "QUOTE_NOT_FOUND", "UNSUPPORTED"];
  let furthest = "URL_UNRESOLVED";
  const further = (reached) => {
    if (order.indexOf(reached) > order.indexOf(furthest)) furthest = reached;
  };
  const passing = [];
  for (const raw of citations) {
    const citation = { url: String(raw?.url ?? ""), quote: String(raw?.quote ?? ""), contentHash: String(raw?.contentHash ?? "") };
    const doc = read.find((d) => d.url === citation.url && d.contentHash === citation.contentHash);
    if (!doc) continue;
    further("QUOTE_NOT_FOUND");
    if (citation.quote.length > maxQuote || !containsQuote(doc.text, citation.quote)) continue;
    further("UNSUPPORTED");
    if (supported(String(claim.text ?? ""), citation, doc)) passing.push({ ...citation, doc });
  }
  return passing.length ? { status: "VERIFIED", reason: null, passing } : { status: "TO_VALIDATE", reason: furthest, passing: [] };
}
