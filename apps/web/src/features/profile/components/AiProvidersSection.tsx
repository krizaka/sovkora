"use client";

import * as React from "react";
import { Button, Icon } from "@krizaka/orazaka-design-system";
import { Dialog } from "@krizaka/ui/dialog";
import { useTranslation } from "@/core/context/LocaleContext";
import { useAiProviders } from "@/features/profile/hooks/useAiProviders";
import { findProvider, PROVIDERS } from "./AiProviderLogos";
import { ProviderConnectionForm } from "./AiProviderConnectionForm";
import { ProviderEmptyState, ProviderRow } from "./AiProvidersParts";

import { SettingsSection } from "@krizaka/ui/settings-layout";

interface AiProvidersSectionProps {
  fetchHeaders: () => Promise<Record<string, string>>;
}

/**
 * AI providers manager — a premium list of the user's configured provider
 * credentials with add / edit / delete actions. Adding or editing opens a modal
 * ({@link Dialog}); all data + lifecycle live in {@link useAiProviders}.
 */
export function AiProvidersSection({ fetchHeaders }: Readonly<AiProvidersSectionProps>) {
  const { t } = useTranslation();
  const {
    isLoading,
    configuredIds,
    availableIds,
    modal,
    draftKey,
    isRevealed,
    isSaving,
    error,
    openAdd,
    openEdit,
    selectProvider,
    closeModal,
    setDraftKey,
    toggleReveal,
    submit,
    remove,
  } = useAiProviders(fetchHeaders);

  const canAdd = availableIds.length > 0;
  const selected = modal.providerId ? findProvider(modal.providerId) : undefined;
  const pickable =
    modal.mode === "edit"
      ? PROVIDERS.filter((p) => p.id === modal.providerId)
      : PROVIDERS.filter((p) => availableIds.includes(p.id));

  return (
    <SettingsSection
      variant="flat"
      id="integrations-providers"
      title={t.providers.title}
      description={t.providers.subtitle}
      footer={
        canAdd && configuredIds.length > 0 ? (
          <Button type="button" size="sm" variant="secondary" onClick={openAdd} className="gap-1.5">
            <Icon name="plus" size={15} />
            {t.providers.addConnection}
          </Button>
        ) : undefined
      }
    >
      {(() => {
        if (isLoading) {
          return <p className="py-4 text-xs text-fg-muted">{t.providers.loading}</p>;
        }
        if (configuredIds.length === 0) {
          return <ProviderEmptyState onAdd={openAdd} />;
        }
        return (
          <>
            <ul className="divide-y divide-border-subtle">
              {configuredIds.map((id) => {
                const provider = findProvider(id);
                if (!provider) return null;
                return (
                  <ProviderRow
                    key={id}
                    provider={provider}
                    onEdit={() => openEdit(id)}
                    onDelete={() => remove(id)}
                    disabled={isSaving}
                  />
                );
              })}
            </ul>
            {!canAdd && <p className="py-3 text-xs text-fg-muted">{t.providers.allConfigured}</p>}
          </>
        );
      })()}

      <Dialog.Root open={modal.mode !== "closed"} onOpenChange={(next) => !next && closeModal()}>
        <Dialog.Content closeLabel={t.providers.cancel}>
          <Dialog.Header>
            <Dialog.Title>
              {modal.mode === "edit" ? t.providers.editConnection : t.providers.addConnection}
            </Dialog.Title>
            <Dialog.Description>{t.providers.modalSubtitle}</Dialog.Description>
          </Dialog.Header>
          <Dialog.Body>
            {modal.mode !== "closed" && (
              <ProviderConnectionForm
                mode={modal.mode}
                pickable={pickable}
                selected={selected}
                onSelect={selectProvider}
                draftKey={draftKey}
                onKeyChange={setDraftKey}
                isRevealed={isRevealed}
                onToggleReveal={toggleReveal}
                isSaving={isSaving}
                error={error}
                onCancel={closeModal}
                onSubmit={submit}
              />
            )}
          </Dialog.Body>
        </Dialog.Content>
      </Dialog.Root>
    </SettingsSection>
  );
}
