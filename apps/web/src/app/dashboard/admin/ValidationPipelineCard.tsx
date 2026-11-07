"use client";

import { useTranslation } from "@/core/context/LocaleContext";
import { Switch } from "@krizaka/ui/switch";
import { Button } from "@krizaka/ui/button";
import * as React from "react";
import { useQuery } from "@krizaka/query";
import { Icon } from "@krizaka/orazaka-design-system";

import { TIER_META, COLOR_CLASSES } from "@/app/dashboard/admin/validationPipeline.meta";
import type { ValidationTierConfig } from "@/app/dashboard/admin/validationPipeline.meta";

import { cn } from "@krizaka/ui/cn";

interface ValidationPipelineCardProps {
  fetchWithAuth: (url: string, options?: RequestInit) => Promise<Response>;
}

export function ValidationPipelineCard({ fetchWithAuth }: Readonly<ValidationPipelineCardProps>) {
  const { t } = useTranslation();
  const [draft, setDraft] = React.useState<ValidationTierConfig[] | null>(null);
  const [saving, setSaving] = React.useState(false);
  const [dirty, setDirty] = React.useState(false);
  const [toast, setToast] = React.useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);

  const showToast = React.useCallback((type: "success" | "error", message: string) => {
    setToast({ type, message });
    setTimeout(() => setToast(null), 3500);
  }, []);

  // The server's list, with the operator's unsaved edits laid over it (a draft) — never copied into state by an effect.
  const config = useQuery({
    queryKey: ["pipeline-validation"],
    queryFn: async () => {
      const res = await fetchWithAuth("/api/v1/pipeline/validation");
      if (!res.ok) throw new Error("load");
      return (await res.json()) as ValidationTierConfig[];
    },
    refetchOnWindowFocus: false,
  });
  const tiers = draft ?? config.data ?? [];
  const setTiers = (next: ValidationTierConfig[] | ((prev: ValidationTierConfig[]) => ValidationTierConfig[])) =>
    setDraft(typeof next === "function" ? next(tiers) : next);
  const loading = config.isPending;
  const shownToast =
    toast ??
    (config.isError ? { type: "error" as const, message: "Failed to load validation pipeline configuration." } : null);

  const handleToggle = (idx: number) => {
    setTiers((prev) => prev.map((tier, i) => (i === idx ? { ...tier, enabled: !tier.enabled } : tier)));
    setDirty(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const res = await fetchWithAuth("/api/v1/pipeline/validation", {
        method: "PUT",
        body: JSON.stringify(tiers),
      });
      if (!res.ok) throw new Error("Failed to save");
      const saved: ValidationTierConfig[] = await res.json();
      setTiers(saved);
      setDirty(false);
      showToast("success", "Validation pipeline saved successfully.");
    } catch {
      showToast("error", "Failed to save validation pipeline configuration.");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <section
        id="validation-pipeline-card"
        className="bg-surface-1/70 border border-border-subtle rounded-2xl p-6 shadow-sm "
      >
        <article className="animate-pulse space-y-3">
          <span className="h-5 w-64 bg-surface-2 rounded block" />
          <span className="h-3 w-96 bg-surface-2 rounded block" />
          {[1, 2, 3, 4].map((i) => (
            <span key={i} className="h-20 bg-surface-1 rounded-xl block" />
          ))}
        </article>
      </section>
    );
  }

  return (
    <section
      id="validation-pipeline-card"
      className="bg-surface-1/70 border border-border-subtle rounded-2xl p-6 shadow-sm relative"
    >
      {/* Toast */}
      {shownToast && (
        <aside
          className={cn(
            "absolute top-4 right-4 z-10 flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium shadow-lg animate-in fade-in slide-in-from-top-2 duration-300",
            shownToast.type === "success"
              ? "bg-success/40 text-success border border-success"
              : "bg-danger/40 text-danger border border-danger",
          )}
        >
          {shownToast.type === "success" ? (
            <Icon name="checkCircle" className="w-4 h-4" />
          ) : (
            <Icon name="alertCircle" className="w-4 h-4" />
          )}
          {shownToast.message}
        </aside>
      )}

      {/* Header */}
      <header className="flex items-center justify-between mb-4">
        <article>
          <h3 className="text-lg font-bold text-fg flex items-center gap-2">
            <span className="inline-flex items-center justify-center w-7 h-7 rounded-lg bg-accent/10 text-accent">
              <Icon name="shield" className="w-4 h-4" />
            </span>
            Validation Matrix
          </h3>
          <p className="text-xs text-fg-muted mt-0.5">Configure the 4-tier autonomous self-correction chain.</p>
        </article>
        <Button
          id="save-validation-pipeline"
          variant="primary"
          size="sm"
          onClick={handleSave}
          disabled={!dirty}
          loading={saving}
        >
          <Icon name="save" className="h-3.5 w-3.5" />
          {saving ? t.admin.pipeline.saving : t.admin.saveChanges}
        </Button>
      </header>

      {/* Tier cards */}
      <ul className="space-y-2">
        {tiers.map((tier, idx) => {
          const meta = TIER_META[tier.stepType] ?? {
            label: tier.stepType,
            badge: "?",
            color: "zinc",
            description: "",
          };
          const colors = COLOR_CLASSES[meta.color] ?? COLOR_CLASSES.amber;

          return (
            <li
              key={tier.id}
              id={`validation-tier-${tier.stepType}`}
              className={cn(
                "group flex items-center gap-4 p-4 rounded-xl border transition-all duration-200",
                tier.enabled
                  ? cn("border-border-subtle bg-surface-0 hover:ring-1", colors.ring, colors.glow)
                  : "border-border-subtle bg-surface-1/50 opacity-60",
              )}
            >
              {/* Tier badge */}
              <span
                className={cn(
                  "flex-shrink-0 w-8 h-8 flex items-center justify-center rounded-lg text-sm font-bold border",
                  colors.badge,
                )}
              >
                {meta.badge}
              </span>

              {/* Info */}
              <article className="flex-1 min-w-0">
                <header className="flex items-center gap-2">
                  <span className="text-sm font-semibold text-fg truncate">{meta.label}</span>
                  <span className="text-[10px] font-mono text-fg-muted hidden sm:inline">
                    order: {tier.executionOrder}
                  </span>
                </header>
                <p className="text-[11px] text-fg-muted truncate mt-0.5">{meta.description}</p>
              </article>

              {/* Disabled badge */}
              {!tier.enabled && (
                <span className="flex-shrink-0 text-[10px] font-semibold text-fg-muted bg-surface-2 px-2 py-0.5 rounded-sm">
                  Disabled
                </span>
              )}

              {/* Toggle */}
              <Switch
                id={`toggle-tier-${tier.stepType}`}
                size="sm"
                checked={tier.enabled}
                onCheckedChange={() => handleToggle(idx)}
                label={meta.label}
              />
            </li>
          );
        })}
      </ul>
    </section>
  );
}
