"use client";

import * as React from "react";
import { parseISO } from "date-fns";
import { Icon } from "@krizaka/orazaka-design-system";
import type { IconName } from "@krizaka/orazaka-design-system";
import { useTranslation } from "@/core/context/LocaleContext";
import { JOB_STATUS } from "@/core/constants/http.constants";

import { cn } from "@krizaka/ui/cn";

/** Shared status-to-stage-index resolver — used by ProgressTimeline and StageLabels */
const resolveStatusIndex = (s: string): number => {
  if (s === JOB_STATUS.COMPLETED || s === JOB_STATUS.FAILED) return 2;
  if (s === JOB_STATUS.PROCESSING || s === JOB_STATUS.PENDING) return 1;
  return 0;
};

/* ─── Fluid Progress Bar ─── */
export function ProgressTimeline({ status }: Readonly<{ status: string }>) {
  const { t } = useTranslation();

  const stages: { key: string; label: string; icon: IconName }[] = [
    {
      key: "submitted",
      label: t.jobs?.colCreated || "Submitted",
      icon: "upload",
    },
    {
      key: "processing",
      label: t.jobs?.running || "Processing",
      icon: "loader",
    },
    {
      key: "done",
      label: status === JOB_STATUS.FAILED ? t.jobs?.statusFailed || "Failed" : t.jobs?.statusCompleted || "Completed",
      icon: status === JOB_STATUS.FAILED ? "error" : "checkCircle",
    },
  ];

  const currentIdx = resolveStatusIndex(status);

  return (
    <section className="flex items-center gap-1 px-1">
      {stages.map((stage, i) => {
        const isDone = i < currentIdx;
        const stageIconName: IconName = isDone ? "check" : stage.icon;
        const isActive = i === currentIdx;
        const isFailed = stage.key === "done" && status === JOB_STATUS.FAILED;

        return (
          <React.Fragment key={stage.key}>
            {/* Node */}
            <div
              className={cn(
                "relative flex items-center justify-center w-9 h-9 rounded-xl transition-all duration-500",
                (() => {
                  if (isDone) return isFailed ? "bg-danger/10 text-danger" : "bg-success/10 text-success";
                  if (isActive) return isFailed ? "bg-danger/10 text-danger" : "bg-accent-soft text-accent";
                  return "bg-surface-2 text-fg-muted";
                })(),
              )}
            >
              {isActive && !isDone && status !== JOB_STATUS.FAILED && status !== JOB_STATUS.COMPLETED && (
                <span className="absolute inset-0 rounded-xl bg-accent opacity-[0.06] animate-pulse" />
              )}
              <Icon
                name={stageIconName}
                className={cn(
                  "w-4 h-4 relative z-10",
                  isActive && (status === JOB_STATUS.PROCESSING || status === JOB_STATUS.PENDING) ? "animate-spin" : "",
                )}
              />
            </div>

            {/* Connector */}
            {i < stages.length - 1 && (
              <div className="flex-1 h-[2px] rounded-sm bg-surface-3 overflow-hidden">
                <div
                  className={cn(
                    "h-full rounded-sm transition-all duration-700 ease-out",
                    (() => {
                      if (isDone || (isActive && i < currentIdx))
                        return isFailed ? "bg-danger w-full" : "bg-accent w-full";
                      if (isActive) return "bg-accent w-1/2 animate-pulse";
                      return "w-0";
                    })(),
                  )}
                />
              </div>
            )}
          </React.Fragment>
        );
      })}
    </section>
  );
}

/* ─── Stage Labels Row ─── */
export function StageLabels({ status }: Readonly<{ status: string }>) {
  const { t } = useTranslation();

  const labels = [
    t.jobs?.colCreated || "Submitted",
    t.jobs?.running || "Processing",
    status === JOB_STATUS.FAILED ? t.jobs?.statusFailed || "Failed" : t.jobs?.statusCompleted || "Completed",
  ];

  const currentIdx = resolveStatusIndex(status);

  return (
    <section className="flex justify-between px-3 mt-1.5">
      {labels.map((label, i) => (
        <span
          key={label}
          className={cn(
            "text-[10px] font-medium transition-colors duration-300",
            i <= currentIdx ? "text-fg" : "text-fg-muted",
          )}
        >
          {label}
        </span>
      ))}
    </section>
  );
}

/* ─── Status Badge (compact) ─── */
export function StatusPill({ status }: Readonly<{ status: string }>) {
  const map: Record<string, { cls: string; label: string; dot: string }> = {
    COMPLETED: { cls: "bg-success/10 text-success", label: "Completed", dot: "bg-success" },
    FAILED: { cls: "bg-danger/10 text-danger", label: "Failed", dot: "bg-danger" },
    PROCESSING: { cls: "bg-warning/10 text-warning", label: "Processing", dot: "bg-warning" },
    PENDING: { cls: "bg-accent/10 text-accent", label: "Pending", dot: "bg-accent" },
  };
  const style = map[status] || map.PENDING;
  const dotColor = style.dot;
  return (
    <span
      className={cn("inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-sm text-[10px] font-semibold", style.cls)}
    >
      <span
        className={cn(
          "w-1.5 h-1.5 rounded-full",
          status === JOB_STATUS.PROCESSING || status === JOB_STATUS.PENDING ? "animate-pulse" : "",
          dotColor,
        )}
      />
      {style.label}
    </span>
  );
}

/* ─── Live Elapsed Counter ─── */
export function LiveElapsed({
  createdAt,
  updatedAt,
  isLive,
}: Readonly<{
  createdAt?: string;
  updatedAt?: string;
  isLive: boolean;
}>) {
  const [now, setNow] = React.useState(() => Date.now());

  React.useEffect(() => {
    if (!isLive) return;
    const interval = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(interval);
  }, [isLive]);

  const startTime = createdAt ? parseISO(createdAt).getTime() : 0;
  const endTime = (() => {
    if (isLive) return now;
    if (updatedAt) return parseISO(updatedAt).getTime();
    return startTime;
  })();
  const diffMs = endTime - startTime;

  const formatElapsed = (ms: number): string => {
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${Math.floor(ms / 60000)}m ${Math.round((ms % 60000) / 1000)}s`;
  };

  const elapsed = formatElapsed(diffMs);

  return (
    <span className="inline-flex items-center gap-1 text-[10px] text-fg-muted font-mono tabular-nums">
      <Icon name="timer" className="w-3 h-3" />
      {elapsed}
      {isLive && <span className="w-1 h-1 rounded-full bg-warning animate-pulse" />}
    </span>
  );
}

/* ─── Tab Item ─── */
export type TabKey = "payload" | "result" | "error";
