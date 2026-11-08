"use client";

import { CheckIcon, InfoIcon, WarningIcon } from "@krizaka/icons";
import { Alert } from "@krizaka/ui/alert";
import { Badge } from "@krizaka/ui/badge";
import { cn } from "@krizaka/ui/cn";
import { DescriptionList, type DescriptionItem } from "@krizaka/ui/description-list";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";

/** A text the pack wrote in both languages. */
type Bilingual = Readonly<Record<string, string | undefined>>;

/** A citation: the contract 1 string, or the contract 2.0 object (its reference, whether the text is pinned, the notice). */
type Citation = string | { reference?: string; pinned?: boolean; notice?: Bilingual };

/** One finding of a rules report (document-validation's shape: rule, verdict, evidence, explanation, citation). */
interface Finding {
  ruleId: string;
  title: string;
  severity?: string;
  verdict: "PASS" | "FAIL" | "NOT_APPLICABLE" | "INSUFFICIENT_EVIDENCE" | string;
  evidenceSpan?: { quote?: string } | null;
  explanation?: string;
  citation?: Citation | null;
}

interface Report {
  findings: Finding[];
  source?: string;
  jurisdiction?: string;
  asOf?: string;
  identification?: { kindLabel?: Bilingual };
  ruleset?: { reviewStatus?: string };
  summary?: { byVerdict?: Record<string, number>; blockingFailures?: number };
}

/** Reads a run artefact as a rules report when it is one (JSON with `findings`), else null. */
export function asReport(value: string): Report | null {
  try {
    const parsed = JSON.parse(value) as Partial<Report>;
    return Array.isArray(parsed.findings) && parsed.findings.every((f) => f && typeof f.verdict === "string")
      ? (parsed as Report)
      : null;
  } catch {
    return null;
  }
}

const VERDICT_LOOK: Record<string, { Glyph: typeof CheckIcon; tone: string }> = {
  PASS: { Glyph: CheckIcon, tone: "text-fg-success" },
  FAIL: { Glyph: WarningIcon, tone: "text-fg-danger" },
};

const SEVERITY_TONE = { BLOCKING: "danger", MAJOR: "warning" } as const;
/** The review state of a ruleset that no lawyer has read yet. */
const UNREVIEWED = "TO_REVIEW_BY_LAWYER";

/** The reference of a citation, whichever contract wrote it. */
export function citationReference(citation: Finding["citation"]): string | null {
  if (!citation) {
    return null;
  }
  return typeof citation === "string" ? citation : (citation.reference ?? null);
}

/** The notice of the first unpinned citation, in the page's language: said once under the list, not on every rule. */
function unpinnedNotice(findings: readonly Finding[], locale: string): string | null {
  for (const finding of findings) {
    const citation = finding.citation;
    if (citation && typeof citation === "object" && citation.pinned === false) {
      return citation.notice?.[locale] ?? citation.notice?.en ?? null;
    }
  }
  return null;
}

const rank: Record<string, number> = { FAIL: 0, INSUFFICIENT_EVIDENCE: 1, PASS: 2, NOT_APPLICABLE: 3 };
const order = (f: Finding) => rank[f.verdict] ?? 1;
const BLOCKING_FIRST: Record<string, number> = { BLOCKING: 0, MAJOR: 1, ADVISORY: 2 };
const bySeverity = (a: Finding, b: Finding) =>
  order(a) - order(b) || (BLOCKING_FIRST[a.severity ?? ""] ?? 3) - (BLOCKING_FIRST[b.severity ?? ""] ?? 3);

/** One rule: its verdict, severity, what it says, the passage it quotes and the law it cites. */
function FindingRow({ finding }: Readonly<{ finding: Finding }>) {
  const { t } = useTranslation();
  const words = t.studio.report;
  const { Glyph, tone } = VERDICT_LOOK[finding.verdict] ?? { Glyph: InfoIcon, tone: "text-fg-warning" };
  const reference = citationReference(finding.citation);
  const severity = finding.severity;
  return (
    <li className="flex gap-3 px-4 py-3">
      <Glyph
        size={18}
        aria-label={words.verdict[finding.verdict] ?? finding.verdict}
        className={cn("mt-0.5 flex-shrink-0", tone)}
      />
      <div className="flex min-w-0 flex-col gap-1">
        <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-[13px] font-semibold text-fg">
          <span className="min-w-0 break-words">{finding.title}</span>
          {severity && finding.verdict === "FAIL" && (
            <Badge size="sm" tone={SEVERITY_TONE[severity as keyof typeof SEVERITY_TONE] ?? "neutral"}>
              {words.severity[severity] ?? severity}
            </Badge>
          )}
          <span className="font-mono text-[11px] font-normal text-fg-muted">{finding.ruleId}</span>
        </p>
        {finding.explanation && <p className="text-[13px] text-fg-secondary">{finding.explanation}</p>}
        {finding.evidenceSpan?.quote && (
          <blockquote className="border-l-2 border-accent/60 pl-3 text-[12px] italic text-fg-secondary">
            « {finding.evidenceSpan.quote} »
          </blockquote>
        )}
        {reference && <p className="text-[11px] text-fg-muted">{reference}</p>}
      </div>
    </li>
  );
}

function FindingList({ findings }: Readonly<{ findings: readonly Finding[] }>) {
  return (
    <ul className="flex flex-col divide-y divide-border-subtle overflow-hidden rounded-xl border border-border-default">
      {findings.map((finding) => (
        <FindingRow key={finding.ruleId} finding={finding} />
      ))}
    </ul>
  );
}

/** The headline in words: the blocking points first, then the others, else « nothing to fix ». */
function headlineOf(words: { blocking: string; toFix: string; noneToFix: string }, failed: number, blocking: number) {
  if (blocking > 0) {
    return fill(words.blocking, { n: blocking });
  }
  return failed > 0 ? fill(words.toFix, { n: failed }) : words.noneToFix;
}

function toneOf(failed: number, blocking: number): "danger" | "warning" | "success" {
  if (blocking > 0) {
    return "danger";
  }
  return failed > 0 ? "warning" : "success";
}

/** The first thing read: how many points to fix (the blocking ones first), then the count of each verdict. */
function Headline({ report }: Readonly<{ report: Report }>) {
  const { t } = useTranslation();
  const words = t.studio.report;
  const counts = report.summary?.byVerdict ?? {};
  const failed = counts.FAIL ?? 0;
  const blocking = report.summary?.blockingFailures ?? 0;
  const title = headlineOf(words, failed, blocking);
  const total = Object.values(counts).reduce((sum, n) => sum + n, 0);
  return (
    <Alert tone={toneOf(failed, blocking)} title={title}>
      {fill(words.summary, { total, pass: counts.PASS ?? 0, fail: failed, na: counts.NOT_APPLICABLE ?? 0 })}
    </Alert>
  );
}

/** What was checked: the document type found, the jurisdiction and the date the rules are those of. */
function Identification({ report }: Readonly<{ report: Report }>) {
  const { t, locale } = useTranslation();
  const words = t.studio.report;
  const kind = report.identification?.kindLabel;
  const candidates: [string, string, string | undefined][] = [
    ["kind", words.docType, kind?.[locale] ?? kind?.en],
    ["jurisdiction", words.jurisdiction, report.jurisdiction],
    ["asOf", words.asOf, report.asOf],
  ];
  const items: DescriptionItem[] = candidates.flatMap(([id, term, details]) =>
    details ? [{ id, term, details }] : [],
  );
  return items.length > 0 ? <DescriptionList layout="grid" items={items} /> : null;
}

/**
 * A rules report as findings a person can read: the headline first, what was checked, the failures (blocking first)
 * each with its severity, the rule it rests on, the passage of the document it quotes and the law it cites; the rules
 * that do not apply are folded. The words are the report's own (the pack writes them); nothing is invented here.
 */
export function ReportFindings({ report }: Readonly<{ report: Report }>) {
  const { t, locale } = useTranslation();
  const words = t.studio.report;
  const findings = [...report.findings].sort(bySeverity);
  const applicable = findings.filter((f) => f.verdict !== "NOT_APPLICABLE");
  const folded = findings.filter((f) => f.verdict === "NOT_APPLICABLE");
  const notice = unpinnedNotice(findings, locale);
  return (
    <div className="flex flex-col gap-4">
      <Headline report={report} />
      <Identification report={report} />
      {report.source && <p className="text-[11px] text-fg-muted">{report.source}</p>}
      <FindingList findings={applicable} />
      {folded.length > 0 && (
        <details className="group rounded-xl border border-border-default">
          <summary className="cursor-pointer rounded-xl px-4 py-3 text-[13px] font-semibold text-fg-secondary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring">
            {fill(words.notApplicable, { n: folded.length })}
          </summary>
          <FindingList findings={folded} />
        </details>
      )}
      {notice && <p className="text-[11px] text-fg-muted">{notice}</p>}
      {report.ruleset?.reviewStatus === UNREVIEWED && <p className="text-[11px] text-fg-muted">{words.review}</p>}
    </div>
  );
}
