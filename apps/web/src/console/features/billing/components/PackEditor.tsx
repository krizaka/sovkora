"use client";

import { Button } from "@krizaka/orazaka-design-system";
import { Card } from "@krizaka/ui/card";
import { type Pack } from "@krizaka/orazaka-shared";
import { BillingField } from "./BillingField";
import { EntitlementRows } from "./EntitlementRows";
import { BILLING_CURRENCY } from "@/console/features/billing/utils/money";

/**
 * The pack form — the **price tag**, which is all billing owns since ADR-036.
 *
 * It used to edit the pack's name, shelf and métier too. Those became catalogue data in
 * the studio context, alongside `studio_i18n`, so a marketing copy change stopped being a
 * deploy of the service that holds the credit ledger. This console does not edit them
 * yet: the category picker, the i18n editor and the Studio multi-select are phase 4 of
 * the Pack plan. Until then a pack's presentation is authored as a `pack` row.
 *
 * The key is therefore the one identifying field here, and it is the opaque string that
 * joins this row to its catalogue entry in the other database. Getting it wrong produces
 * a priced pack nobody can browse — which `PackCoherenceRules` fails the build on.
 */
export function PackEditor({
  pack,
  onChange,
  onSave,
  onCancel,
}: Readonly<{
  pack: Pack;
  onChange: (pack: Pack) => void;
  onSave: () => void;
  onCancel: () => void;
}>) {
  return (
    <Card.Root className="p-5">
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <BillingField
            label="Clé"
            value={pack.packKey}
            onChange={(e) => onChange({ ...pack, packKey: e.target.value })}
          />
          <BillingField
            label={`Prix (centimes, ${BILLING_CURRENCY})`}
            type="number"
            value={String(pack.priceCents)}
            onChange={(e) => onChange({ ...pack, priceCents: Number(e.target.value) })}
          />
          <BillingField
            label="Crédits inclus"
            type="number"
            value={String(pack.includedCredits)}
            onChange={(e) => onChange({ ...pack, includedCredits: Number(e.target.value) })}
          />
        </div>

        <p className="text-xs text-fg-muted">
          Le nom, la catégorie et l’icône du pack vivent dans le catalogue (contexte Studio), pas ici : la facturation
          répond « combien », le catalogue répond « qu’est-ce que c’est ». L’éditeur de catalogue arrive en phase 4.
        </p>

        <div className="space-y-2">
          <p className="text-sm font-medium text-fg">Droits</p>
          <p className="text-xs text-fg-muted">
            Une clé <code>studio.&lt;clé&gt;</code> débloque le Studio du même nom. Ces droits s’ajoutent à ceux de
            l’offre de l’acheteur — ils n’en retirent jamais. Un Studio ajouté au pack sans son droit ici fait échouer
            le build (PACK-001) : l’acheteur paierait et resterait bloqué.
          </p>
          <EntitlementRows
            entitlements={pack.entitlements}
            onChange={(entitlements) => onChange({ ...pack, entitlements })}
          />
        </div>

        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onCancel}>
            Annuler
          </Button>
          <Button onClick={onSave} disabled={!pack.packKey.trim()}>
            Enregistrer
          </Button>
        </div>
      </div>
    </Card.Root>
  );
}
