"use client";

import { IconButton } from "@krizaka/ui/button";
import { Button, Icon } from "@krizaka/orazaka-design-system";
import { useTranslation } from "@/core/context/LocaleContext";
import { ProviderGlyph, type ProviderConfig } from "./AiProviderLogos";

const MASK = "••••••••••••";

/* ─── Configured provider row (list item) ─────────────────────────────────── */
export function ProviderRow({
  provider,
  onEdit,
  onDelete,
  disabled,
}: Readonly<{
  provider: ProviderConfig;
  onEdit: () => void;
  onDelete: () => void;
  disabled: boolean;
}>) {
  const { t } = useTranslation();
  return (
    <li className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-surface-2">
      <ProviderGlyph provider={provider} />
      <div className="min-w-0 flex-1">
        <h4 className="truncate text-sm font-semibold text-fg">{provider.name}</h4>
        <p className="flex items-center gap-1.5 text-xs text-fg-secondary">
          <Icon name="checkCircle" size={13} className="text-success" />
          <span className="text-success">{t.providers.connected}</span>
          <span className="font-mono text-fg-muted">{MASK}</span>
        </p>
      </div>
      <div className="flex items-center gap-1">
        <IconButton
          variant="ghost"
          size="sm"
          className="w-8 px-0"
          onClick={onEdit}
          disabled={disabled}
          label={`${t.providers.editConnection} — ${provider.name}`}
        >
          <Icon name="edit" size={16} />
        </IconButton>
        <IconButton
          variant="ghost"
          size="sm"
          className="w-8 px-0 hover:text-fg-danger"
          onClick={onDelete}
          disabled={disabled}
          label={`${t.providers.deleteProvider} — ${provider.name}`}
        >
          <Icon name="trash" size={16} />
        </IconButton>
      </div>
    </li>
  );
}

/* ─── Empty state (no provider configured) ────────────────────────────────── */
export function ProviderEmptyState({ onAdd }: Readonly<{ onAdd: () => void }>) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-8 text-center">
      <span className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-accent-soft text-accent">
        <Icon name="key" size={22} />
      </span>
      <div className="space-y-1">
        <h4 className="text-sm font-semibold text-fg">{t.providers.emptyTitle}</h4>
        <p className="mx-auto max-w-xs text-xs text-fg-secondary">{t.providers.emptyDesc}</p>
      </div>
      <Button type="button" size="sm" onClick={onAdd} className="mt-1 gap-1.5">
        <Icon name="plus" size={15} />
        {t.providers.emptyCta}
      </Button>
    </div>
  );
}
