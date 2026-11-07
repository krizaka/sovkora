/* eslint-disable no-restricted-syntax */
"use client";

import { Button } from "@krizaka/ui/button";
import { useTranslation } from "@/core/context/LocaleContext";
import React from "react";
import { Icon } from "@krizaka/orazaka-design-system";

/** Structured plan step from the agent's generated execution plan. */
export interface PlanStep {
  id: string;
  label: string;
  description?: string;
  status: "pending" | "approved" | "rejected";
}

interface PlanApprovalCardProps {
  /** The agent-generated plan steps awaiting user approval */
  steps: PlanStep[];
  /** Callback when the user approves the full plan */
  onApprove: () => void;
  /** Callback when the user requests adjustments */
  onRequestAdjustments: () => void;
  /** Whether the approval action is currently processing */
  isProcessing?: boolean;
}

/**
 * Interactive task-matrix card for the AWAITING_APPROVAL conversation state.
 *
 * When the agent suspends execution after plan generation, this component
 * replaces standard Markdown output with an explicit approval gate.
 * The user must either approve the plan or request adjustments.
 *
 * Design: Krizaka razor geometry, frosted glass, ice-blue accent nodes.
 */
export function PlanApprovalCard({
  steps,
  onApprove,
  onRequestAdjustments,
  isProcessing = false,
}: Readonly<PlanApprovalCardProps>) {
  const { t } = useTranslation();
  return (
    <section
      className="glass-card p-0 overflow-hidden animate-in fade-in slide-in-from-bottom-3 duration-500"
      style={{ borderRadius: "var(--kz-radius-lg)" }}
    >
      {/* Header bar */}
      <header className="flex items-center gap-3 px-5 py-4 border-b border-border-subtle bg-surface-2/30">
        <span
          className="inline-flex items-center justify-center w-8 h-8 bg-accent-soft border border-accent/20"
          style={{ borderRadius: "var(--kz-radius-sm)" }}
        >
          <Icon name="shield" size={16} className="text-accent" />
        </span>
        <div>
          <h3 className="text-sm font-bold text-fg tracking-tight">Execution Plan — Awaiting Approval</h3>
          <p className="text-[11px] text-fg-muted mt-0.5">Review the proposed steps before execution proceeds.</p>
        </div>

        {/* Status pill */}
        <span
          className="ml-auto inline-flex items-center gap-1.5 px-3 py-1 text-[10px] font-semibold bg-warning/10 text-warning border border-warning/20"
          style={{ borderRadius: "var(--kz-radius-full)" }}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-warning animate-pulse" />
          Awaiting
        </span>
      </header>

      {/* Step matrix */}
      <ul className="divide-y divide-border-subtle">
        {steps.map((step, idx) => (
          <li
            key={step.id}
            className="flex items-start gap-3 px-5 py-3 transition-colors duration-150 hover:bg-surface-2/30"
          >
            {/* Step index badge */}
            <span
              className="flex-shrink-0 w-6 h-6 flex items-center justify-center text-[10px] font-bold border border-border-default bg-surface-2 text-fg-muted"
              style={{ borderRadius: "var(--kz-radius-sm)" }}
            >
              {idx + 1}
            </span>

            {/* Step content */}
            <div className="flex-1 min-w-0">
              <p className="text-[13px] font-medium text-fg leading-snug">{step.label}</p>
              {step.description && (
                <p className="text-[11px] text-fg-secondary mt-0.5 leading-relaxed">{step.description}</p>
              )}
            </div>

            {/* Status indicator */}
            <span className="flex-shrink-0 mt-0.5">
              {step.status === "approved" ? (
                <Icon name="checkCircle" size={16} className="text-success" />
              ) : step.status === "rejected" ? (
                <Icon name="error" size={16} className="text-danger" />
              ) : (
                <Icon name="circle" size={16} className="text-fg-muted" />
              )}
            </span>
          </li>
        ))}
      </ul>

      {/* Action bar */}
      <footer className="flex items-center gap-3 px-5 py-4 border-t border-border-subtle bg-surface-2/20">
        {/* Primary: Approve */}
        <Button id="plan-approve-btn" variant="primary" size="sm" onClick={onApprove} loading={isProcessing}>
          <Icon name="check" size={14} />
          {t.showcase.chat.approvePlan}
        </Button>

        {/* Secondary: Request Adjustments */}
        <Button
          id="plan-adjust-btn"
          variant="secondary"
          size="sm"
          onClick={onRequestAdjustments}
          disabled={isProcessing}
        >
          <Icon name="edit" size={14} />
          {t.showcase.chat.adjustPlan}
        </Button>
      </footer>
    </section>
  );
}
