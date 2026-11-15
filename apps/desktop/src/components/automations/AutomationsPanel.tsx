import { useCallback, useEffect, useState } from "react";
import { Alert } from "@krizaka/ui/alert";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { EmptyState } from "@krizaka/ui/empty-state";
import { automationsApi, errorMessage, on, type Overview, type Snapshot } from "../../lib/api";
import { useI18n } from "../../lib/i18n";
import { ApprovalsInbox } from "./ApprovalsInbox";
import { AuditLogView } from "./AuditLogView";
import { AutomationCard } from "./AutomationCard";
import { GrantsList } from "./GrantsList";
import { NewAutomationForm } from "./NewAutomationForm";
import { RunHistory } from "./RunHistory";

/** Automations (ADR-079, slice 1): the kill switch, the approvals inbox, the automations, their runs and the audit. */
export function AutomationsPanel({ snap }: Readonly<{ snap: Snapshot }>) {
  const { t, format } = useI18n();
  const [overview, setOverview] = useState<Overview | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [auditKey, setAuditKey] = useState(0);

  const refresh = useCallback(async () => {
    try {
      setOverview(await automationsApi.overview());
      setAuditKey((k) => k + 1);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);

  useEffect(() => {
    void refresh();
    const off = on<unknown>("automations-changed", () => void refresh());
    return () => void off.then((f) => f());
  }, [refresh]);

  const act = (p: Promise<unknown>) => {
    setError(null);
    void p.then(refresh).catch((e) => setError(errorMessage(e)));
  };

  if (!overview) return null;
  const pending = overview.approvals.length;

  return (
    <div className="space-y-8">
      <header className="flex flex-wrap items-start gap-4">
        <div className="min-w-0 flex-1">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            {t("automations.title")}
            {pending > 0 && <Badge tone="warning" dot>{format(t("automations.approvals.count"), { count: pending })}</Badge>}
          </h2>
          <p className="max-w-2xl text-sm text-fg-secondary">{t("automations.lead")}</p>
        </div>
        {overview.halted ? (
          <Button variant="primary" onClick={() => act(automationsApi.setHalted(false))}>{t("automations.resumeAll")}</Button>
        ) : (
          <Button variant="danger" onClick={() => act(automationsApi.setHalted(true))}>{t("automations.killSwitch")}</Button>
        )}
      </header>

      {overview.halted && (
        <Alert tone="warning" title={t("automations.killSwitchOn")}>{t("automations.killSwitchOnLead")}</Alert>
      )}
      {error && <Alert tone="danger">{format(t("common.error"), { message: error })}</Alert>}

      <ApprovalsInbox approvals={overview.approvals} onDecide={(id, d) => act(automationsApi.decide(id, d))} />

      <section aria-labelledby="automations-list" className="space-y-3">
        <div className="flex items-center gap-3">
          <h3 id="automations-list" className="text-sm font-semibold">{t("automations.title")}</h3>
          {!creating && (
            <Button size="sm" variant="primary" shape="cut" className="ml-auto" data-testid="automation-new" onClick={() => setCreating(true)}>{t("automations.new")}</Button>
          )}
        </div>
        {creating && (
          <NewAutomationForm
            roots={snap.roots}
            defaults={overview.defaults}
            onCancel={() => setCreating(false)}
            onCreated={() => {
              setCreating(false);
              void refresh();
            }}
          />
        )}
        {overview.automations.length === 0 && !creating ? (
          <EmptyState
            title={t("automations.noneTitle")}
            description={t("automations.noneLead")}
            action={<Button variant="primary" shape="cut" onClick={() => setCreating(true)}>{t("automations.templateSummarise")}</Button>}
          />
        ) : (
          <div className="grid gap-3 lg:grid-cols-2">
            {overview.automations.map((a) => (
              <AutomationCard key={a.id} automation={a} running={overview.running.includes(a.id)} halted={overview.halted} act={act} />
            ))}
          </div>
        )}
      </section>

      <GrantsList grants={overview.grants} automations={overview.automations} onRevoke={(id) => act(automationsApi.revokeGrant(id))} />
      <RunHistory runs={overview.runs} />
      <AuditLogView refreshKey={auditKey} />
    </div>
  );
}
