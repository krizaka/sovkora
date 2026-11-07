"use client";

import { Tooltip as KzTooltip } from "@krizaka/ui/tooltip";
import { Button, IconButton } from "@krizaka/ui/button";
import * as React from "react";
import { useTranslation } from "@/core/context/LocaleContext";
import { Icon } from "@krizaka/orazaka-design-system";
import { MODEL_CATEGORY } from "@/core/constants/capability.constants";

import { cn } from "@krizaka/ui/cn";

/* ─── Category metadata ─── */
export const CATEGORY_META: Record<string, { icon: string; color: string }> = {
  [MODEL_CATEGORY.SPEECH]: {
    icon: "🎙️",
    color: "bg-accent/10 border-accent/20 text-accent",
  },
  [MODEL_CATEGORY.IMAGE]: {
    icon: "🎨",
    color: "bg-success/10 border-success/20 text-success",
  },
  [MODEL_CATEGORY.VIDEO]: {
    icon: "🎬",
    color: "bg-accent/10 border-accent/20 text-accent",
  },
  [MODEL_CATEGORY.VISION]: {
    icon: "👁️",
    color: "bg-warning/10 border-warning/20 text-warning",
  },
  [MODEL_CATEGORY.AUDIO]: {
    icon: "🔊",
    color: "bg-accent/10 border-accent/20 text-accent",
  },
  theme: {
    icon: "🎨",
    color: "bg-danger/10 border-danger/20 text-danger",
  },
  code: {
    icon: "⚡",
    color: "bg-accent/10 border-accent/20 text-accent",
  },
};

/* ─── Tooltip ─── */
export function Tooltip({ text }: Readonly<{ text: string }>) {
  const { t } = useTranslation();
  return (
    <KzTooltip content={text}>
      <IconButton variant="ghost" size="sm" className="h-6 w-6 px-0" label={t.a11y.help}>
        <Icon name="info" className="h-3.5 w-3.5" />
      </IconButton>
    </KzTooltip>
  );
}

/* ─── Category Selector Grid ─── */
export function CategorySelector({
  categories,
  selectedCategory,
  onSelect,
  disabled,
}: Readonly<{
  categories: { value: string; label: string }[];
  selectedCategory: string;
  onSelect: (value: string) => void;
  disabled: boolean;
}>) {
  return (
    <div className="grid grid-cols-4 sm:grid-cols-7 gap-1.5">
      {categories.map((cat) => {
        const meta = CATEGORY_META[cat.value] || CATEGORY_META.code;
        const isSelected = selectedCategory === cat.value;
        return (
          <Button
            key={cat.value}
            variant="outline"
            disabled={disabled}
            onClick={() => onSelect(cat.value)}
            aria-pressed={isSelected}
            className={cn(
              "h-auto flex-col gap-1 p-2.5 text-xs font-medium",
              isSelected && cn(meta.color, "border-current"),
            )}
          >
            <span className="text-base">{meta.icon}</span>
            <span className="truncate w-full text-center leading-tight">{cat.label}</span>
          </Button>
        );
      })}
    </div>
  );
}
