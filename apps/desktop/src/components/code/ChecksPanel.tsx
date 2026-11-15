import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@krizaka/query";
import { Alert } from "@krizaka/ui/alert";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { codeApi, errorMessage, type CodeCheck, type CodeCheckRun } from "../../lib/api";
import { format, useI18n } from "../../lib/i18n";
import { codeKeys, checksQuery } from "../../lib/queries";

function Command({ check }: Readonly<{ check: CodeCheck }>) {
  const { t } = useI18n();
  return (
    <div className="space-y-1">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-xs">{check.name}</span>
        <Badge tone={check.network ? "warning" : "neutral"}>{t(check.network ? "code.checks.network" : "code.checks.noNetwork")}</Badge>
        <span className="text-xs text-fg-secondary">{format(t("code.checks.timeout"), { secs: check.timeoutSecs })}</span>
      </div>
      {/* the argv exactly as it will run: no shell, nothing interpreted */}
      <code className="block break-all rounded bg-surface p-2 text-xs">{check.argv.join(" ")}</code>
    </div>
  );
}

/**
 * The checks of a repository (ADR-093 §4.4): the repository only suggests; each command is confirmed by the person, once,
 * and can be revoked. After a hunk that changes what runs, the next run asks again. The output is shown as plain text.
 */
export function ChecksPanel() {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [last, setLast] = useState<CodeCheckRun | null>(null);
  const overview = useQuery(checksQuery());
  const refresh = () => queryClient.invalidateQueries({ queryKey: codeKeys.all });
  const onError = (e: unknown) => setError(errorMessage(e));

  const confirm = useMutation({ mutationFn: (c: CodeCheck) => codeApi.confirmCheck(c), onError, onSettled: refresh });
  const revoke = useMutation({ mutationFn: (id: string) => codeApi.revokeCheck(id), onError, onSettled: refresh });
  const run = useMutation({
    mutationFn: (v: { name: string; approved: boolean }) => codeApi.runCheck(v.name, v.approved),
    onSuccess: setLast,
    onError,
    onSettled: refresh,
  });

  const data = overview.data;
  if (!data) return null;
  return (
    <section aria-labelledby="checks-title" className="space-y-3">
      <div>
        <h3 id="checks-title" className="text-sm font-semibold">{t("code.checks.title")}</h3>
        <p className="text-xs text-fg-secondary">{t("code.checks.lead")}</p>
      </div>
      {error && <Alert tone="danger">{error}</Alert>}
      {data.confirmed.length === 0 && <p className="text-sm text-fg-secondary">{t("code.checks.none")}</p>}
      {data.confirmed.map((g) => (
        <Card.Root key={g.id} data-testid="confirmed-check">
          <Card.Body>
            <Command check={g.check} />
            {data.needsApproval && <Alert tone="warning">{t("code.checks.needsApproval")}</Alert>}
            <div className="mt-2 flex gap-2">
              <Button variant="secondary" onClick={() => run.mutate({ name: g.check.name, approved: data.needsApproval })}>
                {t(data.needsApproval ? "code.checks.approveRun" : "code.checks.run")}
              </Button>
              <Button variant="ghost" onClick={() => revoke.mutate(g.id)}>{t("code.checks.revoke")}</Button>
            </div>
          </Card.Body>
        </Card.Root>
      ))}
      {data.suggested.length > 0 && <h4 className="text-xs font-semibold text-fg-secondary">{t("code.checks.suggested")}</h4>}
      {data.suggested.map((c) => (
        <Card.Root key={c.name} data-testid="suggested-check">
          <Card.Body>
            <Command check={c} />
            <div className="mt-2">
              <Button variant="secondary" onClick={() => confirm.mutate(c)}>{t("code.checks.confirm")}</Button>
            </div>
          </Card.Body>
        </Card.Root>
      ))}
      {last && (
        <div className="space-y-1" aria-live="polite">
          <p className="text-xs text-fg-secondary">
            {format(t("code.checks.exit"), { code: last.exitCode ?? "—", ms: last.durationMs })}
            {last.timedOut ? ` · ${t("code.checks.timedOut")}` : ""}
          </p>
          <pre aria-label={t("code.checks.output")} className="max-h-64 overflow-auto whitespace-pre-wrap rounded bg-surface p-2 text-xs">{last.output}</pre>
        </div>
      )}
    </section>
  );
}
