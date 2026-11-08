"use client";

import { IconButton } from "@krizaka/ui/button";
import * as React from "react";
import { Button, Icon, Input } from "@krizaka/orazaka-design-system";
import { useTranslation } from "@/core/context/LocaleContext";
import { ProviderGlyph, type ProviderConfig } from "./AiProviderLogos";

import { cn } from "@krizaka/ui/cn";

/* ─── Reusable masked key field ───────────────────────────────────────────── */
function KeyField({
  value,
  onChange,
  isRevealed,
  onToggleReveal,
  placeholder,
  disabled,
}: Readonly<{
  value: string;
  onChange: (value: string) => void;
  isRevealed: boolean;
  onToggleReveal: () => void;
  placeholder: string;
  disabled: boolean;
}>) {
  const { t } = useTranslation();
  const id = React.useId();
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="text-xs font-medium text-fg-secondary">
        {t.providers.apiKey}
      </label>
      <div className="relative">
        <Input
          id={id}
          type={isRevealed ? "text" : "password"}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          disabled={disabled}
          autoComplete="off"
          className="pr-10 font-mono"
        />
        <IconButton
          variant="ghost"
          size="sm"
          onClick={onToggleReveal}
          disabled={disabled}
          label={isRevealed ? t.providers.keyHide : t.providers.keyReveal}
          className="absolute right-1 top-1/2 w-8 -translate-y-1/2 px-0"
        >
          <Icon name={isRevealed ? "eyeOff" : "eye"} size={16} />
        </IconButton>
      </div>
    </div>
  );
}

/* ─── Add / edit connection form (modal body) ─────────────────────────────── */
export function ProviderConnectionForm({
  mode,
  pickable,
  selected,
  onSelect,
  draftKey,
  onKeyChange,
  isRevealed,
  onToggleReveal,
  isSaving,
  error,
  onCancel,
  onSubmit,
}: Readonly<{
  mode: "add" | "edit";
  pickable: ProviderConfig[];
  selected: ProviderConfig | undefined;
  onSelect: (id: string) => void;
  draftKey: string;
  onKeyChange: (value: string) => void;
  isRevealed: boolean;
  onToggleReveal: () => void;
  isSaving: boolean;
  error: boolean;
  onCancel: () => void;
  onSubmit: () => void;
}>) {
  const { t } = useTranslation();
  const canSubmit = !!selected && draftKey.trim().length > 0 && !isSaving;

  return (
    <form
      className="space-y-4"
      onSubmit={(event) => {
        event.preventDefault();
        if (canSubmit) onSubmit();
      }}
    >
      {mode === "add" ? (
        <fieldset className="space-y-2">
          <legend className="mb-2 text-xs font-medium text-fg-secondary">{t.providers.selectProvider}</legend>
          <div className="grid grid-cols-2 gap-2">
            {pickable.map((provider) => {
              const active = selected?.id === provider.id;
              return (
                <Button
                  key={provider.id}
                  variant="outline"
                  onClick={() => onSelect(provider.id)}
                  aria-pressed={active}
                  className={cn(
                    "h-auto justify-start gap-2.5 p-2.5 text-left font-normal",
                    active && "border-accent bg-accent-soft",
                  )}
                >
                  <ProviderGlyph provider={provider} active={active} />
                  <span className="truncate text-xs font-medium text-fg">{provider.name}</span>
                </Button>
              );
            })}
          </div>
        </fieldset>
      ) : (
        selected && (
          <div className="flex items-center gap-3 rounded-md border border-border-subtle bg-surface-2 p-3">
            <ProviderGlyph provider={selected} active />
            <div>
              <p className="text-sm font-semibold text-fg">{selected.name}</p>
              <p className="text-xs text-fg-secondary">{t.providers.editKeyHint}</p>
            </div>
          </div>
        )
      )}

      <KeyField
        value={draftKey}
        onChange={onKeyChange}
        isRevealed={isRevealed}
        onToggleReveal={onToggleReveal}
        placeholder={selected?.placeholder ?? t.providers.keyPlaceholder}
        disabled={!selected || isSaving}
      />

      {error && (
        <p className="flex items-center gap-1.5 text-xs font-medium text-danger">
          <Icon name="error" size={14} />
          {t.providers.saveError}
        </p>
      )}

      <div className="flex justify-end gap-2 pt-1">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>
          {t.providers.cancel}
        </Button>
        <Button type="submit" size="sm" disabled={!canSubmit} className="gap-1.5">
          {isSaving && <Icon name="loader" size={15} className="animate-spin" />}
          {mode === "edit" ? t.providers.update : t.settings.saveCredentials}
        </Button>
      </div>
    </form>
  );
}
