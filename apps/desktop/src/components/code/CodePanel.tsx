import { useState } from "react";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { EmptyState } from "@krizaka/ui/empty-state";
import { api, codeApi, errorMessage, type CodeApplied, type CodeFileProposal, type CodeRepoInfo } from "../../lib/api";
import { format, useI18n } from "../../lib/i18n";
import { ChecksPanel } from "./ChecksPanel";
import { GithubPanel } from "./GithubPanel";
import { ReviewPanel } from "./ReviewPanel";

/** The Code tab (ADR-093, slice 1): grant a repository, see what Sovkora can see, review proposed diffs per hunk. */
export function CodePanel({ proposals = [] }: Readonly<{ proposals?: CodeFileProposal[] }>) {
  const { t } = useI18n();
  const [repo, setRepo] = useState<CodeRepoInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [written, setWritten] = useState<{ runId: string; applied: CodeApplied[] } | null>(null);

  const choose = async () => {
    setError(null);
    try {
      const folder = await api.pickFolder();
      if (folder) setRepo(await codeApi.open(folder));
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold">{t("code.title")}</h2>
        <p className="text-sm text-fg-secondary">{t("code.lead")}</p>
      </div>
      {error && <Alert tone="danger">{error}</Alert>}
      <Button variant="secondary" onClick={() => void choose()}>{t("code.choose")}</Button>
      {repo ? (
        <div className="space-y-1 text-sm">
          <p className="font-mono text-xs">{repo.root}</p>
          <p>{format(t("code.repo"), { branch: repo.branch, visible: repo.visible, hidden: repo.hidden })}</p>
          {repo.status.length === 0 ? <p className="text-fg-secondary">{t("code.clean")}</p> : <pre className="text-xs">{repo.status.join("\n")}</pre>}
        </div>
      ) : (
        <EmptyState title={t("code.noneTitle")} description={t("code.noneLead")} />
      )}
      <GithubPanel />
      {repo && <ChecksPanel />}
      {repo && proposals.length > 0 && (
        <ReviewPanel
          files={proposals}
          onOpenEditor={(editor, path, line) => void codeApi.openInEditor(editor, path, line).catch((e) => setError(errorMessage(e)))}
          onApply={(decisions) => {
            const runId = `run-${Date.now()}`;
            codeApi.apply(runId, decisions).then((applied) => setWritten({ runId, applied })).catch((e) => setError(errorMessage(e)));
          }}
        />
      )}
      {written && (
        <div className="flex items-center gap-3">
          <span className="text-sm">{format(t("code.review.written"), { count: written.applied.filter((a) => a.written).length })}</span>
          <Button
            variant="ghost"
            onClick={() => void codeApi.undo(written.runId, written.applied.filter((a) => a.written).map((a) => a.path)).then(() => setWritten(null)).catch((e) => setError(errorMessage(e)))}
          >
            {t("code.review.undo")}
          </Button>
        </div>
      )}
    </div>
  );
}
