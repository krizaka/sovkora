import { useState } from "react";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import type { CodeDecision, CodeFileProposal } from "../../lib/api";
import { format, useI18n } from "../../lib/i18n";

type Choice = "approved" | "rejected" | undefined;

/**
 * The review of proposed diffs (ADR-093 §5.5): per file, side by side, every hunk approved or rejected on its own.
 * Nothing is written from here until « Write the approved changes »; a hunk left undecided is not written.
 */
export function ReviewPanel({ files, onApply, onOpenEditor }: Readonly<{
  files: CodeFileProposal[];
  onApply: (decisions: CodeDecision[]) => void;
  onOpenEditor?: (editor: "vs-code" | "xcode", path: string, line: number) => void;
}>) {
  const { t } = useI18n();
  const [choices, setChoices] = useState<Record<string, Choice>>({});
  const key = (path: string, id: number) => `${path}#${id}`;
  const set = (path: string, id: number, c: Choice) => setChoices((s) => ({ ...s, [key(path, id)]: c }));
  const total = files.reduce((n, f) => n + f.hunks.length, 0);
  const approved = files.reduce((n, f) => n + f.hunks.filter((h) => choices[key(f.path, h.id)] === "approved").length, 0);

  const apply = () =>
    onApply(files.map((file) => ({ file, approved: file.hunks.filter((h) => choices[key(file.path, h.id)] === "approved").map((h) => h.id) })));

  return (
    <section aria-labelledby="review-title" className="space-y-3">
      <h3 id="review-title" className="text-sm font-semibold">{t("code.review.title")}</h3>
      {files.map((f) => (
        <Card.Root key={f.path} data-testid="review-file">
          <Card.Body>
            <div className="flex flex-wrap items-center gap-2">
              <Card.Title as="h4"><span className="font-mono">{f.path}</span></Card.Title>
              {f.baseHash === null && <Badge tone="neutral">{t("code.review.newFile")}</Badge>}
              {onOpenEditor && (
                <span className="ml-auto flex gap-2">
                  <Button variant="ghost" onClick={() => onOpenEditor("vs-code", f.path, (f.hunks[0]?.oldStart ?? 0) + 1)}>{t("code.review.openVsCode")}</Button>
                  <Button variant="ghost" onClick={() => onOpenEditor("xcode", f.path, (f.hunks[0]?.oldStart ?? 0) + 1)}>{t("code.review.openXcode")}</Button>
                </span>
              )}
            </div>
            {f.hunks.map((h, i) => {
              const c = choices[key(f.path, h.id)];
              return (
                <div key={h.id} data-testid="hunk" data-state={c ?? "undecided"} className="rounded-md border border-border-subtle bg-surface-2 p-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-xs text-fg-secondary">{format(t("code.review.hunk"), { n: i + 1, total: f.hunks.length, line: h.oldStart + 1 })}</span>
                    {h.execSurface && <Badge tone="warning">{t("code.review.execSurface")}</Badge>}
                    {h.addsSecret && <Badge tone="danger">{t("code.review.addsSecret")}</Badge>}
                    {c && <Badge tone={c === "approved" ? "success" : "neutral"}>{t(`code.review.${c}`)}</Badge>}
                  </div>
                  <div className="mt-2 grid gap-2 md:grid-cols-2">
                    <pre aria-label={t("code.review.removed")} className="max-h-48 overflow-auto whitespace-pre-wrap rounded bg-surface p-2 text-xs text-fg-secondary">{h.oldLines.join("") || " "}</pre>
                    <pre aria-label={t("code.review.added")} className="max-h-48 overflow-auto whitespace-pre-wrap rounded bg-surface p-2 text-xs text-fg">{h.newLines.join("") || " "}</pre>
                  </div>
                  <div className="mt-2 flex gap-2">
                    <Button variant="secondary" aria-pressed={c === "approved"} onClick={() => set(f.path, h.id, "approved")}>{t("code.review.approve")}</Button>
                    <Button variant="ghost" aria-pressed={c === "rejected"} onClick={() => set(f.path, h.id, "rejected")}>{t("code.review.reject")}</Button>
                  </div>
                </div>
              );
            })}
          </Card.Body>
        </Card.Root>
      ))}
      <div className="flex items-center gap-3">
        <Button variant="primary" disabled={approved === 0} onClick={apply}>{t("code.review.apply")}</Button>
        <span className="text-xs text-fg-secondary">{format(t("code.review.summary"), { approved, total })}</span>
      </div>
    </section>
  );
}
