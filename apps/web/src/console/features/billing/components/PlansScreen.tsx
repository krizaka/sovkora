"use client";

import { useCallback, useEffect, useState } from "react";
import { Badge, Button } from "@krizaka/orazaka-design-system";
import { Card } from "@krizaka/ui/card";
import { formatCredits, type Plan } from "@krizaka/orazaka-shared";
import { BILLING_CURRENCY, money } from "@/console/features/billing/utils/money";
import { BillingField } from "./BillingField";
import { EntitlementRows } from "./EntitlementRows";
import { BillingAdminApi } from "@/console/services/billing.admin.api";

/**
 * The plans screen.
 *
 * Editing the entitlement matrix here is the design's robustness test: a fourth
 * plan must be creatable with zero deploy (§12). Nothing about a plan is compiled
 * in, so the matrix is a free-form list of typed keys rather than a fixed set of
 * checkboxes — a new entitlement key needs no front-end change either.
 */
export function PlansScreen() {
  const [plans, setPlans] = useState<Plan[]>([]);
  const [editing, setEditing] = useState<Plan | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setPlans(await BillingAdminApi.listPlans(true));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Chargement impossible");
    }
  }, []);

  useEffect(() => {
    // Deferred rather than called inline: loading synchronously inside the effect
    // sets state during the same commit and cascades a second render.
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  const save = async () => {
    if (!editing) return;
    try {
      await BillingAdminApi.savePlan(editing);
      setEditing(null);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Enregistrement impossible");
    }
  };

  return (
    <section className="space-y-6">
      <header className="flex items-start justify-between">
        <div>
          <h2 className="text-lg font-semibold text-fg">Offres</h2>
          <p className="text-sm text-fg-muted">
            Prix, dotation mensuelle et matrice de droits. Une offre retirée reste visible ici : des abonnements y font
            encore référence.
          </p>
        </div>
        <Button
          onClick={() =>
            setEditing({
              planKey: "",
              label: "",
              tierRank: plans.length * 10 + 10,
              monthlyCreditGrant: 0,
              priceCents: 0,
              currency: BILLING_CURRENCY,
              rateLimitTierKey: "default",
              isPublic: true,
              isActive: true,
              entitlements: [],
            })
          }
        >
          Nouvelle offre
        </Button>
      </header>

      {error && <p className="text-sm text-fg-danger">{error}</p>}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {plans.map((plan) => (
          <Card.Root key={plan.planKey} className="p-5">
            <article className="space-y-3">
              <header className="flex items-start justify-between">
                <hgroup>
                  <p className="font-medium text-fg">{plan.label}</p>
                  <p className="text-xs text-fg-muted">{plan.planKey}</p>
                </hgroup>
                {!plan.isActive && <Badge variant="warning">retirée</Badge>}
              </header>
              <p className="text-sm text-fg">
                {money(plan.priceCents, plan.currency)} · {formatCredits(plan.monthlyCreditGrant)} crédits / mois
              </p>
              <p className="text-xs text-fg-muted">{plan.entitlements.length} droits</p>
              <Button size="sm" variant="ghost" onClick={() => setEditing(plan)}>
                Modifier
              </Button>
            </article>
          </Card.Root>
        ))}
      </div>

      {editing && <PlanEditor plan={editing} onChange={setEditing} onSave={save} onCancel={() => setEditing(null)} />}
    </section>
  );
}

function PlanEditor({
  plan,
  onChange,
  onSave,
  onCancel,
}: {
  plan: Plan;
  onChange: (plan: Plan) => void;
  onSave: () => void;
  onCancel: () => void;
}) {
  return (
    <Card.Root className="p-5">
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <BillingField
            label="Clé"
            value={plan.planKey}
            onChange={(e) => onChange({ ...plan, planKey: e.target.value })}
          />
          <BillingField
            label="Libellé"
            value={plan.label}
            onChange={(e) => onChange({ ...plan, label: e.target.value })}
          />
          <BillingField
            label="Rang (ordonne les offres)"
            type="number"
            value={String(plan.tierRank)}
            onChange={(e) => onChange({ ...plan, tierRank: Number(e.target.value) })}
          />
          <BillingField
            label={`Prix (centimes, ${plan.currency || BILLING_CURRENCY})`}
            type="number"
            value={String(plan.priceCents)}
            onChange={(e) => onChange({ ...plan, priceCents: Number(e.target.value) })}
          />
          <BillingField
            label="Crédits / mois"
            type="number"
            value={String(plan.monthlyCreditGrant)}
            onChange={(e) => onChange({ ...plan, monthlyCreditGrant: Number(e.target.value) })}
          />
          <BillingField
            label="Palier de débit"
            value={plan.rateLimitTierKey}
            onChange={(e) => onChange({ ...plan, rateLimitTierKey: e.target.value })}
          />
        </div>

        <div className="space-y-2">
          <p className="text-sm font-medium text-fg">Droits</p>
          <EntitlementRows
            entitlements={plan.entitlements}
            onChange={(entitlements) => onChange({ ...plan, entitlements })}
          />
        </div>

        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onCancel}>
            Annuler
          </Button>
          <Button onClick={onSave} disabled={!plan.planKey.trim() || !plan.label.trim()}>
            Enregistrer
          </Button>
        </div>
      </div>
    </Card.Root>
  );
}
