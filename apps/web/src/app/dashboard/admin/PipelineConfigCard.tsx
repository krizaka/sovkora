"use client";

import { Button } from "@krizaka/ui/button";
import * as React from "react";
import { useQuery } from "@krizaka/query";
import { Icon } from "@krizaka/orazaka-design-system";
import { useTranslation } from "@/core/context/LocaleContext";
import { PipelineInterceptorRow } from "@/app/dashboard/admin/PipelineInterceptorRow";
import type { InterceptorConfig } from "@/app/dashboard/admin/pipelineConfig.types";

import { cn } from "@krizaka/ui/cn";

interface PipelineConfigCardProps {
  fetchWithAuth: (url: string, options?: RequestInit) => Promise<Response>;
}

export function PipelineConfigCard({ fetchWithAuth }: Readonly<PipelineConfigCardProps>) {
  const { t } = useTranslation();
  const pl = t.admin.pipeline;

  const [draft, setDraft] = React.useState<InterceptorConfig[] | null>(null);
  const [saving, setSaving] = React.useState(false);
  const [toast, setToast] = React.useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);
  const [dragIdx, setDragIdx] = React.useState<number | null>(null);
  const [dirty, setDirty] = React.useState(false);

  const showToast = React.useCallback((type: "success" | "error", message: string) => {
    setToast({ type, message });
    setTimeout(() => setToast(null), 3500);
  }, []);

  // The server's list, with the operator's unsaved edits laid over it (a draft) — never copied into state by an effect.
  const config = useQuery({
    queryKey: ["pipeline-interceptors"],
    queryFn: async () => {
      const res = await fetchWithAuth("/api/v1/pipeline/interceptors");
      if (!res.ok) throw new Error("load");
      return (await res.json()) as InterceptorConfig[];
    },
    refetchOnWindowFocus: false,
  });
  const interceptors = draft ?? config.data ?? [];
  const setInterceptors = (next: InterceptorConfig[] | ((prev: InterceptorConfig[]) => InterceptorConfig[])) =>
    setDraft(typeof next === "function" ? next(interceptors) : next);
  const loading = config.isPending;
  const shownToast = toast ?? (config.isError ? { type: "error" as const, message: pl.errorLoading } : null);

  const handleDragStart = (idx: number) => setDragIdx(idx);

  const handleDragOver = (e: React.DragEvent, overIdx: number) => {
    e.preventDefault();
    if (dragIdx === null || dragIdx === overIdx) return;
    const updated = [...interceptors];
    const [dragged] = updated.splice(dragIdx, 1);
    updated.splice(overIdx, 0, dragged);
    const reordered = updated.map((item, i) => ({
      ...item,
      executionOrder: i + 1,
    }));
    setInterceptors(reordered);
    setDragIdx(overIdx);
    setDirty(true);
  };

  const handleDragEnd = () => setDragIdx(null);

  const handleToggle = (idx: number) => {
    setInterceptors((prev) => prev.map((item, i) => (i === idx ? { ...item, enabled: !item.enabled } : item)));
    setDirty(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const res = await fetchWithAuth("/api/v1/pipeline/interceptors", {
        method: "PUT",
        body: JSON.stringify(interceptors),
      });
      if (!res.ok) throw new Error(pl.errorSaving);
      const saved: InterceptorConfig[] = await res.json();
      setInterceptors(saved);
      setDirty(false);
      showToast("success", pl.savedSuccess);
    } catch {
      showToast("error", pl.errorSaving);
    } finally {
      setSaving(false);
    }
  };

  const handleReset = async () => {
    if (!confirm(pl.resetConfirm)) return;
    setSaving(true);
    try {
      const res = await fetchWithAuth("/api/v1/pipeline/interceptors/reset", { method: "POST" });
      if (!res.ok) throw new Error(pl.errorSaving);
      const defaults: InterceptorConfig[] = await res.json();
      setInterceptors(defaults);
      setDirty(false);
      showToast("success", pl.savedSuccess);
    } catch {
      showToast("error", pl.errorSaving);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <section className="bg-surface-1/70 border border-border-subtle rounded-2xl p-6 shadow-sm ">
        <article className="animate-pulse space-y-3">
          <span className="h-5 w-56 bg-surface-2 rounded block" />
          <span className="h-3 w-80 bg-surface-2 rounded block" />
          {[1, 2, 3, 4].map((i) => (
            <span key={i} className="h-14 bg-surface-1 rounded-xl block" />
          ))}
        </article>
      </section>
    );
  }

  return (
    <section className="bg-surface-1/70 border border-border-subtle rounded-2xl p-6 shadow-sm relative">
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
            <span className="inline-flex items-center justify-center w-7 h-7 rounded-lg bg-warning/10 text-warning">
              ⚡
            </span>
            {pl.title}
          </h3>
          <p className="text-xs text-fg-secondary mt-0.5">{pl.subtitle}</p>
        </article>
        <nav className="flex items-center gap-2">
          <Button variant="secondary" size="sm" onClick={handleReset} disabled={saving}>
            <Icon name="refresh" className="h-3.5 w-3.5" />
            {pl.resetToDefault}
          </Button>
          <Button variant="primary" size="sm" onClick={handleSave} disabled={!dirty} loading={saving}>
            <Icon name="save" className="h-3.5 w-3.5" />
            {saving ? pl.saving : pl.saveOrder}
          </Button>
        </nav>
      </header>

      {/* Drag hint */}
      <div className="flex items-center gap-1.5 mb-3 text-fg-muted text-fg-secondary">
        <Icon name="info" className="w-3 h-3" />
        {pl.dragHint}
      </div>

      {/* Interceptor list */}
      <ul className="space-y-1.5">
        {interceptors.map((item, idx) => (
          <PipelineInterceptorRow
            key={item.interceptorKey}
            item={item}
            idx={idx}
            dragIdx={dragIdx}
            disabledLabel={pl.disabled}
            enabledLabel={pl.enabledLabel}
            onDragStart={handleDragStart}
            onDragOver={handleDragOver}
            onDragEnd={handleDragEnd}
            onToggle={handleToggle}
          />
        ))}
      </ul>
    </section>
  );
}
