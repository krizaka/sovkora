/**
 * @file report.ts
 * @description Reads a Studio result as typed data, the way the web client does (ReportFindings): a rules report
 * (`findings[]` with a verdict) is drawn as cards, a model's text as Markdown. Pure, so it is tested without a screen.
 */

export type Tone = "success" | "danger" | "warning" | "neutral";

export interface Finding {
  readonly id: string;
  readonly title: string;
  readonly tone: Tone;
  readonly verdict: string;
  readonly explanation: string | null;
  readonly quote: string | null;
  readonly citation: string | null;
}

export interface Report {
  readonly findings: readonly Finding[];
  readonly source: string | null;
  readonly counts: readonly (readonly [string, number])[];
}

export interface TextResult {
  readonly content: string;
  /** The run's own facts (provider, model, duration…), kept apart from the words the person reads. */
  readonly facts: readonly (readonly [string, string])[];
}

const str = (value: unknown): string | null => (typeof value === "string" && value !== "" ? value : null);

function toneOf(verdict: string): Tone {
  const v = verdict.toLowerCase();
  if (v === "pass" || v === "consistent") return "success";
  if (v === "fail" || v === "inconsistent") return "danger";
  if (v === "info" || v === "not-examined" || v === "insufficient_evidence") return "warning";
  return "neutral";
}

function asRecord(value: string): Record<string, unknown> | null {
  const trimmed = value.trim();
  if (!trimmed.startsWith("{")) return null;
  try {
    const parsed: unknown = JSON.parse(trimmed);
    return typeof parsed === "object" && parsed !== null && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

/** A rules report (JSON with `findings`) as cards — failures first — or null when the value is something else. */
export function asReport(value: string): Report | null {
  const record = asRecord(value);
  const raw = record?.findings;
  if (!Array.isArray(raw) || raw.length === 0) return null;
  const findings: Finding[] = [];
  for (const entry of raw as unknown[]) {
    if (typeof entry !== "object" || entry === null) return null;
    const f = entry as Record<string, unknown>;
    const verdict = str(f.verdict) ?? str(f.status);
    if (verdict === null) return null;
    const span = f.evidenceSpan as Record<string, unknown> | null | undefined;
    findings.push({
      id: str(f.ruleId) ?? str(f.id) ?? str(f.code) ?? String(findings.length + 1),
      title: str(f.title) ?? str(f.ruleId) ?? verdict,
      tone: toneOf(verdict),
      verdict,
      explanation: str(f.explanation) ?? str(f.observation),
      quote: str(span?.quote) ?? str(f.evidence),
      citation: str(f.citation) ?? str(f.mayIndicate),
    });
  }
  const rank = (t: Tone) => {
    if (t === "danger") return 0;
    return t === "success" ? 2 : 1;
  };
  findings.sort((a, b) => rank(a.tone) - rank(b.tone));
  const byVerdict = (record?.summary as { byVerdict?: Record<string, number> } | undefined)?.byVerdict;
  const counts = Object.entries(byVerdict ?? {}).filter(([, n]) => typeof n === "number");
  return { findings, source: str(record?.source), counts };
}

const FACT_KEYS = ["provider", "model", "durationMs", "seed", "runId"] as const;

/**
 * A model's text, with the run's facts split from the words. A step can answer `{"content": "…", "provider": …,
 * "durationMs": …}`: the person reads the content; the rest is facts, never printed into the text.
 */
export function asTextResult(value: string): TextResult {
  const record = asRecord(value);
  const content = str(record?.content) ?? str(record?.text);
  if (record === null || content === null) return { content: value, facts: [] };
  const facts: (readonly [string, string])[] = [];
  for (const key of FACT_KEYS) {
    const v = record[key];
    if (typeof v === "string" || typeof v === "number") {
      facts.push([key, key === "durationMs" ? `${(Number(v) / 1000).toFixed(1)} s` : String(v)]);
    }
  }
  return { content, facts };
}
