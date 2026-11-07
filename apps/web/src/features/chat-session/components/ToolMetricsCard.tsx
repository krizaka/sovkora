"use client";

import React from "react";
import { Card } from "@krizaka/ui/card";
import { Icon } from "@krizaka/orazaka-design-system";

interface ToolMetricsPayload {
  type: "tool_metrics";
  toolName: string;
  data: Record<string, string | number>;
}

interface ToolMetricsCardProps {
  payload: ToolMetricsPayload;
  /** The caption ("Metrics"), translated. */
  label: string;
}

/**
 * ToolMetricsCard — generative UI for a structured tool metrics payload: when the stream holds a
 * `{"type":"tool_metrics"}` block, the figures are drawn as a @krizaka/ui card (`Card.Stat` per figure) instead of
 * plain markdown.
 *
 * @example Payload shape:
 * ```json
 * {"type":"tool_metrics","toolName":"doctor","data":{"cpu":"82%","memory":"4.2GB","latency":"120ms"}}
 * ```
 */
export function ToolMetricsCard({ payload, label }: Readonly<ToolMetricsCardProps>) {
  return (
    <Card.Root className="w-full max-w-lg animate-in fade-in slide-in-from-bottom-2 duration-300">
      <Card.Body className="gap-3">
        <header className="flex items-center gap-2 border-b border-border-subtle pb-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-md bg-accent-soft text-fg-accent">
            <Icon name="sliders" size={14} />
          </span>
          <Card.Title className="text-xs group-hover:text-fg">{payload.toolName}</Card.Title>
          <span className="ml-auto font-mono text-[10px] text-fg-muted">{label}</span>
        </header>
        <section className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {Object.entries(payload.data).map(([key, value]) => (
            <Card.Stat
              key={key}
              label={formatLabel(key)}
              className="rounded-md border border-border-subtle bg-surface-2 p-2 [&>p:last-child]:font-mono [&>p:last-child]:text-sm"
            >
              {value}
            </Card.Stat>
          ))}
        </section>
      </Card.Body>
    </Card.Root>
  );
}

/**
 * Attempts to parse a raw string as a ToolMetrics JSON payload.
 * Returns the parsed payload or null if not a valid tool_metrics block.
 */
export function parseToolMetrics(raw: string): ToolMetricsPayload | null {
  try {
    // Check for JSON block markers in markdown
    const jsonMatch = raw.match(/```(?:json)?\s*(\{[\s\S]*?"type"\s*:\s*"tool_metrics"[\s\S]*?\})\s*```/);
    const toParse = jsonMatch ? jsonMatch[1] : raw;
    const parsed = JSON.parse(toParse);
    if (parsed?.type === "tool_metrics" && typeof parsed.toolName === "string" && typeof parsed.data === "object") {
      return parsed as ToolMetricsPayload;
    }
  } catch {
    // Not a tool_metrics payload — fall through
  }
  return null;
}

/** Converts camelCase/snake_case keys to human-readable labels. */
function formatLabel(key: string): string {
  return key
    .replace(/_/g, " ")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .toLowerCase();
}
