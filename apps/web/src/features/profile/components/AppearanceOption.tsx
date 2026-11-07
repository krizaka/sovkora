"use client";

import * as React from "react";
import { cn } from "@krizaka/ui/cn";
import { RadioGroup } from "@krizaka/ui/radio-group";
import type { Appearance } from "@/core/hooks/useAppearance";

/**
 * How a preview reads its tokens. A theme the kit can scope (`.theme-dark`, `.theme-<name>` — design system 2.1)
 * is drawn with the roles inside its island, so the preview shows the real overridden tokens. Light has no island
 * yet (@krizaka/tokens declares it on `html.light` only): it is drawn with the invariant tokens, and "system" is
 * half light, half dark.
 */
export type PreviewIsland = { kind: "island"; className: string } | { kind: "light" } | { kind: "system" };

export function previewIsland(value: Appearance): PreviewIsland {
  if (value === "light") return { kind: "light" };
  if (value === "system") return { kind: "system" };
  return { kind: "island", className: value === "dark" ? "theme-dark" : `theme-${value}` };
}

interface AppearanceOptionProps {
  value: Appearance;
  label: string;
  desc: string;
  icon: React.ReactNode;
  isActive: boolean;
  /** Just chosen: a short pulse confirms the click. */
  pulse?: boolean;
  clickToApplyLabel: string;
}

/** A miniature of the app (sidebar, header, text, an accent button) written with roles only. */
function MiniApp({ light = false, className }: Readonly<{ light?: boolean; className?: string }>) {
  const surface = light ? "bg-fg-on-media" : "bg-surface-0";
  const panel = light ? "bg-fg-on-media/90" : "bg-surface-1";
  const line = light ? "bg-scrim/25" : "bg-fg-muted";
  const edge = light ? "border-scrim/10" : "border-border-subtle";
  return (
    <span aria-hidden className={cn("absolute inset-0 block rounded-none", surface, className)}>
      <span className={cn("absolute bottom-0 left-0 top-0 block w-[22px] border-r", panel, edge)}>
        <span className="flex flex-col gap-1.5 px-1 pt-3">
          <span className="block h-2.5 w-2.5 rounded-sm bg-accent/60" />
          <span className={cn("block h-1 w-2.5 rounded-sm opacity-60", line)} />
          <span className={cn("block h-1 w-2.5 rounded-sm opacity-40", line)} />
        </span>
      </span>
      <span className={cn("absolute left-[22px] right-0 top-0 block h-[14px] border-b", panel, edge)}>
        <span className={cn("absolute left-2 top-1/2 block h-1 w-5 -translate-y-1/2 rounded-sm opacity-50", line)} />
      </span>
      <span className="absolute bottom-0 left-[22px] right-0 top-[14px] flex flex-col gap-[5px] p-2">
        <span className={cn("block h-[5px] w-[65%] rounded-sm opacity-60", line)} />
        <span className={cn("block h-[5px] w-[45%] rounded-sm opacity-45", line)} />
        <span className={cn("block h-[5px] w-[55%] rounded-sm opacity-50", line)} />
        <span className="mt-auto flex items-center gap-1.5">
          <span className="block h-[7px] w-[28px] rounded-sm bg-accent" />
          <span className={cn("block h-[5px] w-[18px] rounded-sm opacity-30", line)} />
        </span>
      </span>
    </span>
  );
}

function Preview({ value }: Readonly<{ value: Appearance }>) {
  const island = previewIsland(value);
  if (island.kind === "island") {
    return (
      <span data-island={island.className} className={cn("absolute inset-0 block", island.className)}>
        <MiniApp />
      </span>
    );
  }
  if (island.kind === "light") return <MiniApp light />;
  return (
    <>
      <MiniApp light />
      <span
        data-island="theme-dark"
        className="theme-dark absolute inset-0 block [clip-path:polygon(55%_0,100%_0,100%_100%,45%_100%)]"
      >
        <MiniApp />
      </span>
    </>
  );
}

/**
 * One appearance of the picker: a `RadioGroup.Card` from @krizaka/ui (the whole card is the radio, its words name it)
 * holding a miniature of the app drawn in the theme it offers (its tokens, read through the kit's islands), the
 * animated border of the chosen one and hover guidance. The group ({@link AppearancePicker}) owns the choice.
 */
export function AppearanceOption({
  value,
  label,
  desc,
  icon,
  isActive,
  pulse,
  clickToApplyLabel,
}: Readonly<AppearanceOptionProps>) {
  return (
    <RadioGroup.Card
      value={value}
      id={`theme-card-${value}`}
      className={cn(
        "group relative cursor-pointer gap-0 overflow-hidden rounded-xl p-0",
        "duration-250 transition-all ease-out focus-visible:ring-offset-2",
        "animation-fade-up",
        pulse && "theme-card-pulse",
        isActive
          ? "theme-card-active border-transparent bg-surface-1 shadow-lg"
          : "border-border-subtle opacity-70 hover:border-border-default hover:opacity-100 hover:shadow-md",
      )}
    >
      <figure className="relative h-[80px] w-full overflow-hidden bg-surface-2">
        <Preview value={value} />
        {isActive && (
          <mark className="theme-check-pop absolute right-1 top-1 flex h-[18px] w-[18px] items-center justify-center rounded-sm bg-success shadow-md">
            <svg
              aria-hidden
              className="h-2.5 w-2.5 text-on-accent"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={3}
              stroke="currentColor"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
            </svg>
          </mark>
        )}
        {!isActive && (
          <figcaption
            aria-hidden
            className="absolute inset-0 flex items-center justify-center bg-transparent transition-colors duration-200 group-hover:bg-scrim/30"
          >
            <span className="rounded-sm bg-scrim px-2 py-0.5 text-[10px] font-medium text-fg-on-media opacity-0 transition-opacity duration-200 group-hover:opacity-100">
              {clickToApplyLabel}
            </span>
          </figcaption>
        )}
      </figure>

      <span className="flex w-full items-center gap-1.5 bg-surface-1 px-3 pb-0.5 pt-2.5">
        <span aria-hidden className="text-fg/60 transition-colors group-hover:text-fg/90">
          {icon}
        </span>
        <span className="truncate text-[11px] font-semibold text-fg">{label}</span>
      </span>
      <span className="w-full bg-surface-1 px-3 pb-2.5 text-[10px] leading-tight text-fg-muted">{desc}</span>
    </RadioGroup.Card>
  );
}
