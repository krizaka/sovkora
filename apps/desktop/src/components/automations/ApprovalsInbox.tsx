import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { baseName, slotLabel, type Approval, type Decision } from "../../lib/api";
import { useI18n } from "../../lib/i18n";

/** The approvals inbox: what a run wants to write, shown in full before anything is written (ADR-079 §4). */
export function ApprovalsInbox({ approvals, onDecide }: Readonly<{ approvals: Approval[]; onDecide: (id: string, decision: Decision) => void }>) {
  const { t, format } = useI18n();
  if (approvals.length === 0) return null;
  return (
    <section aria-labelledby="approvals-title" className="space-y-3">
      <div>
        <h3 id="approvals-title" className="text-sm font-semibold">{t("automations.approvals.title")}</h3>
        <p className="text-xs text-fg-secondary">{t("automations.approvals.lead")}</p>
      </div>
      {approvals.map((a) => (
        <Card.Root key={a.id} className="border-warning/60" data-testid="approval">
          <Card.Body>
            <div className="flex flex-wrap items-center gap-2">
              <Card.Title as="h4">{a.automationName}</Card.Title>
              <Badge tone="warning">{format(t("automations.approvals.tool"), { tool: `${a.server}.${a.tool}`, folder: baseName(a.scope) })}</Badge>
              <span className="ml-auto text-xs text-fg-secondary">
                {format(t("automations.approvals.expires"), { time: slotLabel(a.expiresAt, t("common.dateLocale")) ?? a.expiresAt })}
              </span>
            </div>
            <ul className="space-y-2">
              {a.writes.map((w) => (
                <li key={w.path} className="rounded-md border border-border-subtle bg-surface-2 p-3">
                  <p className="font-mono text-xs text-fg">{baseName(w.path)}</p>
                  <pre className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap text-xs text-fg-secondary">{w.preview}</pre>
                </li>
              ))}
            </ul>
            <p className="text-xs text-fg-secondary">{t("automations.approvals.alwaysHint")}</p>
            <div className="flex flex-wrap gap-2">
              <Button variant="primary" onClick={() => onDecide(a.id, "once")}>{t("automations.approvals.once")}</Button>
              <Button variant="secondary" onClick={() => onDecide(a.id, "always")}>{t("automations.approvals.always")}</Button>
              <Button variant="ghost" onClick={() => onDecide(a.id, "deny")}>{t("automations.approvals.deny")}</Button>
            </div>
          </Card.Body>
        </Card.Root>
      ))}
    </section>
  );
}
