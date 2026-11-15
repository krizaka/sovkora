// Scoring is deterministic: the same answers always give the same verdicts. The rules are the verifier's (verify.mjs) and the golden case's own labels; nothing is judged by a model.
import { createHash } from "node:crypto";
import { verifyClaim } from "./verify.mjs";

/** The body of a ```-fenced answer (an optional `json` tag dropped, the edges trimmed), or null when the text is not fenced. */
function unfence(s) {
  const mark = "```";
  if (s.length < 2 * mark.length || !s.startsWith(mark) || !s.endsWith(mark)) return null;
  const body = s.slice(mark.length, -mark.length);
  return (body.startsWith("json") ? body.slice("json".length) : body).trim();
}

export function parseAnswer(text) {
  let s = String(text ?? "").trim();
  let fenced = false;
  const fence = unfence(s);
  if (fence !== null) {
    s = fence;
    fenced = true;
  }
  if (!s.startsWith("{")) return { ok: false, fenced, claims: [] };
  try {
    const root = JSON.parse(s);
    if (!root || !Array.isArray(root.claims) || typeof root.answer !== "string") return { ok: false, fenced, claims: [] };
    return { ok: true, fenced, claims: root.claims.filter((c) => c && typeof c === "object") };
  } catch {
    return { ok: false, fenced, claims: [] };
  }
}

const FR_WORDS = new Set("le la les un une des du de au aux et ou est sont doit peut ne pas que qui dans pour par sur avec sans son sa ses il elle lui leur à en ce cette".split(" "));
const EN_WORDS = new Set("the a an of to and or is are must may not that which in for by on with without his her its he she it their this".split(" "));

export function languageOf(text) {
  const words = text.toLowerCase().match(/[a-zà-ÿ']+/gu) ?? [];
  const fr = words.filter((w) => FR_WORDS.has(w)).length;
  const en = words.filter((w) => EN_WORDS.has(w)).length;
  if (fr === en) return "unknown";
  return fr > en ? "fr" : "en";
}

/** The fixed rubric of a claim's wording, 0 to 4: one point each for the right language, plain text, a complete sentence, a length that reads (20 to 260 characters). */
export function wordingScore(text, language) {
  let points = 0;
  if (languageOf(text) === language) points++;
  if (!/[`*#_]|\[|\]|<|>|\p{Extended_Pictographic}/u.test(text)) points++;
  if (/^[A-ZÀ-ÖØ-Þ"«]/u.test(text.trim()) && /[.!?»]$/u.test(text.trim())) points++;
  if (text.length >= 20 && text.length <= 260) points++;
  return points;
}

const regex = (source) => new RegExp(source, "iu");

/** Scores one answer of one model on one golden case. */
export function scoreCase(goldenCase, passages, answerText) {
  const parsed = parseAnswer(answerText);
  const refsOfCase = new Set(goldenCase.targets.flatMap((t) => t.refs));
  const decoys = new Set(goldenCase.decoys ?? []);
  const forbidden = (goldenCase.forbidden ?? []).map((f) => regex(f.pattern));
  const offTopic = goldenCase.offTopic ? regex(goldenCase.offTopic) : null;
  const verdicts = parsed.claims.map((claim) => {
    const verdict = verifyClaim(claim, passages);
    const text = String(claim.text ?? "");
    const cited = [...new Set(verdict.passing.map((p) => p.doc.ref))];
    let wrong = null;
    if (verdict.status === "VERIFIED") {
      if (cited.every((ref) => decoys.has(ref) || !refsOfCase.has(ref))) wrong = "cites only articles the kind does not target";
      else if (forbidden.some((f) => f.test(text))) wrong = "states what the law does not say";
      else if (offTopic?.test(text)) wrong = "off topic";
    }
    return { status: verdict.status, reason: verdict.reason, cited, wrong, text };
  });
  const verified = verdicts.filter((v) => v.status === "VERIFIED");
  const citations = parsed.claims.flatMap((c) => (Array.isArray(c.citations) ? c.citations : []));
  const resolvable = citations.filter((c) => passages.some((p) => p.url === c?.url && p.contentHash === c?.contentHash));
  const quoteFound = resolvable.filter((c) => passages.find((p) => p.url === c.url && p.contentHash === c.contentHash) && verifyClaim({ text: "", citations: [c] }, passages).status === "VERIFIED").length;
  const citedRefs = new Set(verified.flatMap((v) => v.cited));
  const expected = goldenCase.expectedRefs;
  const stated = (goldenCase.mustState ?? []).filter((m) => verified.some((v) => regex(m.pattern).test(v.text)));
  const wording = parsed.claims.length ? parsed.claims.reduce((sum, c) => sum + wordingScore(String(c.text ?? ""), goldenCase.language), 0) / (4 * parsed.claims.length) : 0;
  const verdictKey = createHash("sha256")
    .update(JSON.stringify(verdicts.map((v) => [v.status, v.reason, v.cited, v.wrong])))
    .digest("hex")
    .slice(0, 12);
  return {
    parsed: parsed.ok,
    fenced: parsed.fenced,
    claims: parsed.claims.length,
    verified: verified.length,
    wrongAccepted: verdicts.filter((v) => v.wrong).length,
    wrongReasons: verdicts.filter((v) => v.wrong).map((v) => v.wrong),
    citations: citations.length,
    quotesFound: quoteFound,
    expected: expected.length,
    recalled: expected.filter((ref) => citedRefs.has(ref)).length,
    mustState: (goldenCase.mustState ?? []).length,
    stated: stated.length,
    wording,
    verdictKey,
  };
}

const percentile = (values, p) => {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
};
const ratio = (num, den) => (den === 0 ? null : num / den);

/** One model over all its runs: the metrics of the issue. `runs` are { caseId, language, score, ms, costUsd, usage }. */
export function aggregate(runs) {
  const sum = (field) => runs.reduce((s, r) => s + r.score[field], 0);
  const frRuns = runs.filter((r) => r.language === "fr" && r.score.claims > 0);
  const costs = runs.map((r) => r.costUsd);
  return {
    runs: runs.length,
    parsed: runs.filter((r) => r.score.parsed).length,
    claims: sum("claims"),
    verified: sum("verified"),
    wrongAccepted: sum("wrongAccepted"),
    verifiedRate: ratio(sum("verified"), sum("claims")),
    quoteFoundRate: ratio(sum("quotesFound"), sum("citations")),
    recall: ratio(sum("recalled"), sum("expected")),
    statedRate: ratio(sum("stated"), sum("mustState")),
    frenchWording: frRuns.length ? frRuns.reduce((s, r) => s + r.score.wording, 0) / frRuns.length : null,
    latencyP50: percentile(runs.map((r) => r.ms), 50),
    latencyP95: percentile(runs.map((r) => r.ms), 95),
    promptTokens: runs.reduce((s, r) => s + r.usage.promptTokens, 0),
    completionTokens: runs.reduce((s, r) => s + r.usage.completionTokens, 0),
    costUsd: costs.every((c) => c == null) ? null : costs.reduce((s, c) => s + (c ?? 0), 0),
  };
}

/** Same inputs, same verdicts: the share of (case) groups whose repeated runs gave one verdict key. */
export function stability(runs) {
  const byCase = new Map();
  for (const r of runs) byCase.set(r.caseId, [...(byCase.get(r.caseId) ?? []), r.score.verdictKey]);
  const repeated = [...byCase.values()].filter((keys) => keys.length > 1);
  return repeated.length ? repeated.filter((keys) => new Set(keys).size === 1).length / repeated.length : null;
}

/**
 * The decision rule of the issue: a model that had one wrong citation accepted is disqualified; among the others the best accuracy
 * (recall of the expected articles, then facts stated, then verified rate) wins; on a tie the cheapest, preferring local (sovereign).
 * `models` is { id: { aggregate, local, costPerRunUsd } }.
 */
export function decide(models) {
  const safe = Object.entries(models).filter(([, m]) => m.aggregate.wrongAccepted === 0);
  const disqualified = Object.entries(models).filter(([id]) => !safe.some(([sid]) => sid === id)).map(([id, m]) => ({ id, wrongAccepted: m.aggregate.wrongAccepted }));
  const accuracy = (m) => [m.aggregate.recall ?? 0, m.aggregate.statedRate ?? 0, m.aggregate.verifiedRate ?? 0];
  const cmp = ([, a], [, b]) => {
    const x = accuracy(a);
    const y = accuracy(b);
    for (let i = 0; i < x.length; i++) if (Math.abs(x[i] - y[i]) > 1e-9) return y[i] - x[i];
    if (a.local !== b.local) return a.local ? -1 : 1;
    return (a.costPerRunUsd ?? 0) - (b.costPerRunUsd ?? 0);
  };
  const ranked = [...safe].sort(cmp);
  return { winner: ranked[0]?.[0] ?? null, ranking: ranked.map(([id]) => id), disqualified };
}
