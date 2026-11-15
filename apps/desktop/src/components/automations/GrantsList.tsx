import { Button } from "@krizaka/ui/button";
import { baseName, type AutomationView, type Grant } from "../../lib/api";
import { useI18n } from "../../lib/i18n";

/** The permanent permissions ("approve always"): narrow, listed, revocable (ADR-079 §4). */
export function GrantsList({ grants, automations, onRevoke }: Readonly<{ grants: Grant[]; automations: AutomationView[]; onRevoke: (id: string) => void }>) {
  const { t, format } = useI18n();
  const nameOf = (id: string) => automations.find((a) => a.id === id)?.name ?? id;
  return (
    <section aria-labelledby="grants-title" className="space-y-2">
      <h3 id="grants-title" className="text-sm font-semibold">{t("automations.grants.title")}</h3>
      {grants.length === 0 ? (
        <p className="text-xs text-fg-secondary">{t("automations.grants.none")}</p>
      ) : (
        <ul className="divide-y divide-border-subtle rounded-md border border-border-subtle">
          {grants.map((g) => (
            <li key={g.id} className="flex items-center gap-3 px-3 py-2 text-sm" data-testid="grant">
              <span className="min-w-0 flex-1 truncate">
                {format(t("automations.grants.item"), { automation: nameOf(g.automationId), tool: `${g.server}.${g.tool}`, folder: baseName(g.scope) })}
              </span>
              <Button size="sm" variant="ghost" onClick={() => onRevoke(g.id)}>{t("automations.grants.revoke")}</Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
