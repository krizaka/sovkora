"use client";

import * as React from "react";
import { format, parseISO } from "date-fns";
import { fr, enUS } from "date-fns/locale";
import { Button, Icon, Input } from "@krizaka/orazaka-design-system";
import { useTranslation } from "@/core/context/LocaleContext";
import type { ApiKeyInfo, GeneratedKey } from "@/features/profile/hooks/useApiKeys";

/** Empty-state card body shown when the user owns no API keys yet. */
export function ApiKeysEmptyState({ onCreate }: Readonly<{ onCreate: () => void }>) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col items-center gap-3 px-4 py-8 text-center">
      <span className="inline-flex h-10 w-10 items-center justify-center rounded-full bg-accent-soft text-accent">
        <Icon name="key" size={20} />
      </span>
      <div className="space-y-1">
        <p className="text-sm font-medium text-fg">{t.apiKeys.emptyTitle}</p>
        <p className="max-w-sm text-xs text-fg-muted">{t.apiKeys.emptyDesc}</p>
      </div>
      <Button type="button" size="sm" onClick={onCreate} className="gap-1.5">
        <Icon name="plus" size={15} />
        {t.apiKeys.emptyCta}
      </Button>
    </div>
  );
}

/** A single API-key row: label, prefix, timestamps and a revoke action. */
export function ApiKeyRow({
  apiKey,
  onDelete,
  disabled,
}: Readonly<{
  apiKey: ApiKeyInfo;
  onDelete: () => void;
  disabled: boolean;
}>) {
  const { t, locale } = useTranslation();
  const dfLocale = locale === "fr" ? fr : enUS;
  const lastUsed = apiKey.lastUsedAt
    ? `${t.apiKeys.lastUsed}: ${format(parseISO(apiKey.lastUsedAt), "PP", { locale: dfLocale })}`
    : t.apiKeys.neverUsed;

  return (
    <li className="flex items-center justify-between gap-3 px-4 py-3">
      <div className="min-w-0 space-y-1">
        <div className="flex items-center gap-2">
          <p className="truncate text-sm font-medium text-fg">{apiKey.name}</p>
          <code className="rounded bg-surface-2 px-1.5 py-0.5 font-mono text-[11px] text-fg-secondary">
            {apiKey.keyPrefix}…
          </code>
        </div>
        <p className="text-[11px] text-fg-muted">
          {t.apiKeys.created}: {format(parseISO(apiKey.createdAt), "PP", { locale: dfLocale })}
          {" · "}
          {lastUsed}
        </p>
      </div>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={onDelete}
        disabled={disabled}
        className="shrink-0 gap-1.5 text-danger"
      >
        <Icon name="trash" size={15} />
        {t.apiKeys.revoke}
      </Button>
    </li>
  );
}

/** Create-key form body rendered inside the create dialog. */
export function CreateApiKeyForm({
  draftName,
  onNameChange,
  isSaving,
  error,
  onCancel,
  onSubmit,
}: Readonly<{
  draftName: string;
  onNameChange: (value: string) => void;
  isSaving: boolean;
  error: "none" | "limit" | "generic";
  onCancel: () => void;
  onSubmit: () => void;
}>) {
  const { t } = useTranslation();
  return (
    <form
      className="space-y-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
    >
      <div className="space-y-1.5">
        <label htmlFor="api-key-name" className="text-xs font-medium text-fg-secondary">
          {t.apiKeys.nameLabel}
        </label>
        <Input
          id="api-key-name"
          value={draftName}
          onChange={(event) => onNameChange(event.target.value)}
          placeholder={t.apiKeys.namePlaceholder}
          maxLength={100}
          autoFocus
        />
      </div>
      {error !== "none" && (
        <p className="text-xs text-danger">{error === "limit" ? t.apiKeys.limitReached : t.apiKeys.createError}</p>
      )}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>
          {t.apiKeys.cancel}
        </Button>
        <Button type="submit" size="sm" disabled={isSaving || !draftName.trim()}>
          {t.apiKeys.createCta}
        </Button>
      </div>
    </form>
  );
}

/** One-time reveal of the freshly created plaintext secret, with copy-to-clipboard. */
export function RevealKeyPanel({ generated, onDone }: Readonly<{ generated: GeneratedKey; onDone: () => void }>) {
  const { t } = useTranslation();
  const [copied, setCopied] = React.useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(generated.key);
      setCopied(true);
    } catch {
      // Clipboard may be unavailable — the secret remains selectable in the field.
    }
  };

  return (
    <div className="space-y-4">
      <p className="text-xs text-fg-muted">{t.apiKeys.revealDesc}</p>
      <div className="flex items-center gap-2">
        <code className="min-w-0 flex-1 truncate rounded-md border border-border-subtle bg-surface-2 px-3 py-2 font-mono text-xs text-fg">
          {generated.key}
        </code>
        <Button type="button" variant="outline" size="sm" onClick={copy} className="shrink-0 gap-1.5">
          <Icon name={copied ? "check" : "copy"} size={15} />
          {copied ? t.apiKeys.copied : t.apiKeys.copy}
        </Button>
      </div>
      <div className="flex justify-end">
        <Button type="button" size="sm" onClick={onDone}>
          {t.apiKeys.done}
        </Button>
      </div>
    </div>
  );
}
