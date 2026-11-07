"use client";

import React from "react";
import { Icon, type IconName } from "@krizaka/orazaka-design-system";

import { cn } from "@krizaka/ui/cn";

/** Single interceptor node metadata */
interface PipelineNode {
  id: string;
  label: string;
  icon: IconName;
  order: number;
  status: "active" | "bypassed" | "idle";
  aiDep: boolean;
}

/** Full interceptor chain — matches §2.10 Context-Matrix Orchestration Pipeline */
const PIPELINE_NODES: PipelineNode[] = [
  { id: "user-ctx", label: "UserCtx", icon: "context", order: 1, status: "active", aiDep: false },
  { id: "sys-ctx", label: "SysCtx", icon: "system", order: 2, status: "active", aiDep: false },
  { id: "lang", label: "Language", icon: "language", order: 3, status: "active", aiDep: false },
  { id: "memory", label: "Memory", icon: "memory", order: 5, status: "active", aiDep: false },
  { id: "rag", label: "RAG", icon: "rag", order: 5, status: "idle", aiDep: false },
  { id: "mcp", label: "MCP", icon: "mcp", order: 5, status: "idle", aiDep: false },
  { id: "refiner", label: "Refiner", icon: "refiner", order: 6, status: "active", aiDep: true },
  { id: "router", label: "Router", icon: "router", order: 7, status: "active", aiDep: true },
  { id: "tool", label: "Tool", icon: "tool", order: 8, status: "idle", aiDep: false },
  { id: "cost", label: "CostShield", icon: "costShield", order: 9, status: "active", aiDep: false },
  { id: "validation", label: "Validation", icon: "quantum", order: 10, status: "active", aiDep: true },
];

const STATUS_BORDER_CLASSES: Record<PipelineNode["status"], string> = {
  active: "border-success",
  bypassed: "border-fg-muted",
  idle: "border-fg-muted",
};

const STATUS_BG_CLASSES: Record<PipelineNode["status"], string> = {
  active: "bg-success/8",
  bypassed: "bg-transparent",
  idle: "bg-transparent",
};

/**
 * InterceptorPipeline — Visual strip showing the 11-node interceptor chain.
 * Each node: circular icon + label, connected by gradient trace lines.
 * Status: active (green pulse), idle (dim), bypassed (strikethrough).
 */
export function InterceptorPipeline() {
  return (
    <div className="glass-card rounded-lg p-(--orazaka-space-card) overflow-hidden">
      <div className="flex items-center gap-2 mb-4">
        <Icon name="pipeline" size={16} className="text-accent" />
        <h3 className="hud-label !mb-0">INTERCEPTOR PIPELINE</h3>
        <span className="ml-auto text-[10px] font-mono text-success flex items-center gap-1.5">
          <span className="status-dot text-success" />
          OPERATIONAL
        </span>
      </div>

      {/* Pipeline strip — horizontal scroll on mobile, flex-wrap on desktop */}
      <div className="flex items-center gap-1 overflow-x-auto pb-2 scrollbar-thin">
        {PIPELINE_NODES.map((node, idx) => (
          <React.Fragment key={node.id}>
            {/* Node */}
            <div
              className={cn(
                "pipeline-node group flex-shrink-0 flex flex-col items-center gap-1.5 px-2 py-2 rounded-md transition-all duration-200 hover:bg-surface-2 cursor-default",
                node.status === "active" ? "opacity-100" : "opacity-40",
              )}
              title={`${node.label} — ${node.status}${node.aiDep ? " (AI)" : ""}`}
            >
              {/* Icon container with status ring */}
              <div
                className={cn(
                  "relative flex items-center justify-center w-9 h-9 rounded-full border transition-colors duration-200",
                  STATUS_BORDER_CLASSES[node.status],
                  STATUS_BG_CLASSES[node.status],
                )}
              >
                <Icon name={node.icon} size={16} className="transition-colors duration-200" label={node.label} />
                {/* Active pulse ring */}
                {node.status === "active" && (
                  <span
                    className={cn(
                      "absolute inset-0 rounded-sm animate-[pulse-ring_2s_ease-in-out_infinite] border border-solid",
                      STATUS_BORDER_CLASSES[node.status],
                      "opacity-30",
                    )}
                  />
                )}
                {/* AI dependency badge */}
                {node.aiDep && (
                  <span className="absolute -top-0.5 -right-0.5 w-3 h-3 rounded-full bg-accent flex items-center justify-center">
                    <Icon name="spark" size={7} className="text-on-accent" />
                  </span>
                )}
              </div>

              {/* Label */}
              <span className="text-[9px] font-semibold text-fg-muted group-hover:text-fg-secondary transition-colors whitespace-nowrap">
                {node.label}
              </span>
            </div>

            {/* Connector edge */}
            {idx < PIPELINE_NODES.length - 1 && (
              <div className="pipeline-edge flex-shrink-0 w-4 h-px bg-gradient-to-r from-border-default to-border-subtle relative">
                {/* Data flow particle */}
                <span className="absolute top-1/2 -translate-y-1/2 w-1 h-1 rounded-full bg-accent animate-[shimmer_2s_linear_infinite] opacity-60" />
              </div>
            )}
          </React.Fragment>
        ))}
      </div>
    </div>
  );
}
