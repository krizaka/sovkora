"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Badge, Button } from "@krizaka/orazaka-design-system";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import {
  capabilityForModelCategory,
  isModelPriced,
  suggestedUnitFor,
  type CatalogModel,
  type MarginPreview,
  type PricebookRate,
} from "@krizaka/orazaka-shared";
import { ModelRateEditor } from "./ModelRateEditor";
import { asRate, type ModelRateDraft } from "./ModelRateDraft";
import { BillingAdminApi } from "@/console/services/billing.admin.api";

/**
 * Per-model pricing.
 *
 * The screen exists because a model with no rate is not merely untracked — the
 * hold refuses it outright rather than guessing a price, so the capability stops
 * working for that model entirely. Adding an AI model to the catalogue and
 * forgetting to price it is therefore an outage with a billing-shaped cause, and
 * the only way to notice used to be a 409 in a log.
 *
 * Unpriced models are listed first for that reason: this screen answers "what is
 * broken" before it answers "what does it cost".
 *
 * The capability and the suggested unit come from the model's catalogue category,
 * but both stay editable — the unit is a property of the model, not of its
 * category, which is why speech and transcription share AUDIO and bill
 * differently.
 */
export function ModelPricingScreen() {
  const [models, setModels] = useState<CatalogModel[]>([]);
  const [rates, setRates] = useState<PricebookRate[]>([]);
  const [draft, setDraft] = useState<ModelRateDraft | null>(null);
  const [preview, setPreview] = useState<MarginPreview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [catalogue, pricebook] = await Promise.all([BillingAdminApi.listModels(), BillingAdminApi.listRates()]);
      setModels(catalogue);
      setRates(pricebook);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Chargement impossible");
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  const rows = useMemo(() => {
    const decorated = models.map((model) => {
      const capability = capabilityForModelCategory(model.category);
      const own = rates.find((rate) => rate.capability === capability && rate.modelName === model.modelName);
      const fallback = rates.find((rate) => rate.capability === capability && rate.modelName === null);
      return { model, capability, own, fallback, priced: isModelPriced(model, rates) };
    });
    // Unpriced first: these are the models that currently refuse every request.
    return decorated.sort((a, b) => Number(a.priced) - Number(b.priced));
  }, [models, rates]);

  const unpriced = rows.filter((row) => !row.priced).length;

  const startDraft = (model: CatalogModel, existing?: PricebookRate) => {
    setPreview(null);
    setDraft({
      capability: capabilityForModelCategory(model.category),
      modelName: model.modelName,
      unit: existing?.unit ?? suggestedUnitFor(model.category),
      creditsPerUnit: existing?.creditsPerUnit ?? 1,
      minimumCredits: existing?.minimumCredits ?? 1,
      estimateCredits: existing?.estimateCredits ?? 1,
      isReprice: Boolean(existing),
    });
  };

  const runPreview = async () => {
    if (!draft) return;
    setIsBusy(true);
    try {
      setPreview(await BillingAdminApi.previewRate(asRate(draft)));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Aperçu impossible");
    } finally {
      setIsBusy(false);
    }
  };

  const publish = async () => {
    if (!draft) return;
    setIsBusy(true);
    try {
      await BillingAdminApi.publishRate(asRate(draft));
      setDraft(null);
      setPreview(null);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Publication impossible");
    } finally {
      setIsBusy(false);
    }
  };

  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { id: "model", header: "Modèle", card: "title", value: ({ model }) => model.modelLabel || model.modelName },
    { id: "category", header: "Catégorie", value: ({ model }) => model.category },
    { id: "capability", header: "Capacité" },
    { id: "unit", header: "Unité", value: ({ own, fallback }) => own?.unit ?? fallback?.unit ?? "—" },
    {
      id: "creditsPerUnit",
      header: "Crédits / unité",
      align: "end",
      value: ({ own, fallback }) => own?.creditsPerUnit ?? fallback?.creditsPerUnit ?? "—",
    },
    {
      id: "estimateCredits",
      header: "Estimation",
      align: "end",
      value: ({ own, fallback }) => own?.estimateCredits ?? fallback?.estimateCredits ?? "—",
    },
    {
      id: "status",
      header: "Statut",
      cell: ({ own, priced }) => (
        <>
          {!priced && <Badge variant="danger">non tarifé</Badge>}
          {priced && own && <Badge variant="success">tarif dédié</Badge>}
          {priced && !own && <Badge variant="default">tarif par défaut</Badge>}
        </>
      ),
    },
    {
      id: "actions",
      header: "",
      align: "end",
      cell: ({ model, own }) => (
        <Button size="sm" variant="ghost" onClick={() => startDraft(model, own)}>
          {own ? "Modifier" : "Tarifer"}
        </Button>
      ),
    },
  ];

  return (
    <section className="space-y-6">
      <header>
        <h2 className="text-lg font-semibold text-fg">Tarification par modèle</h2>
        <p className="text-sm text-fg-muted">
          Un modèle sans tarif ni tarif par défaut est refusé à la réservation : la capacité cesse de fonctionner pour
          ce modèle. Les modèles non tarifés sont affichés en premier.
        </p>
      </header>

      {unpriced > 0 && (
        <p className="text-sm text-fg-warning">
          {unpriced} modèle(s) sans tarif — toute requête les utilisant sera refusée.
        </p>
      )}
      {error && <p className="text-sm text-fg-danger">{error}</p>}

      <DataTable
        caption="Tarification par modèle"
        rows={rows}
        columns={columns}
        getRowId={(row) => row.model.modelName}
        labels={{ loading: "Lecture des modèles", empty: "Aucun modèle." }}
      />

      {draft && (
        <ModelRateEditor
          draft={draft}
          preview={preview}
          isBusy={isBusy}
          onChange={(next) => {
            setDraft(next);
            // Any edit invalidates the replay it was not run against.
            setPreview(null);
          }}
          onPreview={runPreview}
          onPublish={publish}
          onCancel={() => setDraft(null)}
        />
      )}
    </section>
  );
}
