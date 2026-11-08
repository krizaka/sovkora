"use client";

import { Button } from "@krizaka/orazaka-design-system";
import { Card } from "@krizaka/ui/card";
import type { BillableUnit, MarginPreview } from "@krizaka/orazaka-shared";
import { BillingField } from "./BillingField";
import { UNITS, type ModelRateDraft } from "./ModelRateDraft";

export interface ModelRateEditorProps {
  draft: ModelRateDraft;
  preview: MarginPreview | null;
  isBusy: boolean;
  onChange: (draft: ModelRateDraft) => void;
  onPreview: () => void;
  onPublish: () => void;
  onCancel: () => void;
}

/**
 * The per-model rate form.
 *
 * Every edit clears the preview, because a preview describes the proposal it was
 * run against — leaving a stale one on screen would let an admin publish one rate
 * having reviewed another.
 */
export function ModelRateEditor({
  draft,
  preview,
  isBusy,
  onChange,
  onPreview,
  onPublish,
  onCancel,
}: ModelRateEditorProps) {
  const update = (patch: Partial<ModelRateDraft>) => onChange({ ...draft, ...patch });

  return (
    <Card.Root className="p-5">
      <article className="space-y-4">
        <header>
          <h3 className="text-sm font-semibold text-fg">{draft.modelName}</h3>
          <p className="text-xs text-fg-muted">
            Facturé comme {draft.capability}. Une publication clôture le tarif courant et ouvre une nouvelle version.
            {draft.isReprice && !preview ? " Simulez sur 30 jours avant de publier." : ""}
          </p>
        </header>

        <fieldset className="grid gap-3 sm:grid-cols-2">
          <label className="block space-y-1.5">
            <span className="text-xs font-medium text-fg-secondary">Unité de mesure</span>
            <select
              className="flex h-11 w-full rounded-lg border border-border-subtle bg-surface-2 px-3 text-sm text-fg"
              value={draft.unit}
              onChange={(e) => update({ unit: e.target.value as BillableUnit })}
            >
              {UNITS.map((unit) => (
                <option key={unit} value={unit}>
                  {unit}
                </option>
              ))}
            </select>
          </label>
          <BillingField
            label="Crédits par unité"
            type="number"
            step="0.0001"
            value={String(draft.creditsPerUnit)}
            onChange={(e) => update({ creditsPerUnit: Number(e.target.value) })}
          />
          <BillingField
            label="Minimum facturé"
            type="number"
            value={String(draft.minimumCredits)}
            onChange={(e) => update({ minimumCredits: Number(e.target.value) })}
          />
          <BillingField
            label="Estimation réservée (hold)"
            type="number"
            value={String(draft.estimateCredits)}
            onChange={(e) => update({ estimateCredits: Number(e.target.value) })}
          />
        </fieldset>

        {preview && (
          <p className="rounded-lg border border-border-subtle p-3 text-sm text-fg">
            {preview.sampleEvents === 0
              ? "Aucun trafic sur 30 jours — publication sans données."
              : `Sur ${preview.sampleEvents} requêtes : ${preview.currentCredits} → ${preview.proposedCredits} crédits.`}
          </p>
        )}

        <footer className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onCancel}>
            Annuler
          </Button>
          <Button variant="secondary" onClick={onPreview} disabled={isBusy}>
            Simuler sur 30 jours
          </Button>
          {/*
            Repricing needs the 30-day replay first (design §12). A model priced for
            the first time has no traffic by construction — it was being refused — so
            demanding a preview there would be a click that teaches nothing.
          */}
          <Button onClick={onPublish} disabled={isBusy || (draft.isReprice && !preview)}>
            Publier
          </Button>
        </footer>
      </article>
    </Card.Root>
  );
}
