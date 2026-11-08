"use client";

import * as React from "react";
import { Button, Icon } from "@krizaka/orazaka-design-system";
import { Dialog } from "@krizaka/ui/dialog";
import { useTranslation } from "@/core/context/LocaleContext";
import { MAX_API_KEYS, useApiKeys } from "@/features/profile/hooks/useApiKeys";
import { ApiKeyRow, ApiKeysEmptyState, CreateApiKeyForm, RevealKeyPanel } from "./ApiKeysParts";

import { SettingsSection } from "@krizaka/ui/settings-layout";

interface ApiKeysSectionProps {
  fetchHeaders: () => Promise<Record<string, string>>;
}

/**
 * Inbound API keys manager — lists the user's Personal Access Tokens with create
 * and revoke actions. Creating opens a modal ({@link Dialog}); the fresh secret is
 * then surfaced once through a reveal dialog. All data + lifecycle live in
 * {@link useApiKeys}.
 */
export function ApiKeysSection({ fetchHeaders }: Readonly<ApiKeysSectionProps>) {
  const { t } = useTranslation();
  const {
    isLoading,
    keys,
    canCreate,
    isCreateOpen,
    draftName,
    isSaving,
    error,
    generated,
    openCreate,
    closeCreate,
    setDraftName,
    submit,
    dismissGenerated,
    remove,
  } = useApiKeys(fetchHeaders);

  return (
    <SettingsSection
      variant="flat"
      id="api-keys"
      title={t.apiKeys.title}
      description={t.apiKeys.subtitle}
      footer={
        keys.length > 0 ? (
          <Button
            type="button"
            size="sm"
            variant="secondary"
            onClick={openCreate}
            disabled={!canCreate}
            className="gap-1.5"
          >
            <Icon name="plus" size={15} />
            {t.apiKeys.create}
          </Button>
        ) : undefined
      }
    >
      {(() => {
        if (isLoading) {
          return <p className="py-4 text-xs text-fg-muted">{t.apiKeys.loading}</p>;
        }
        if (keys.length === 0) {
          return <ApiKeysEmptyState onCreate={openCreate} />;
        }
        return (
          <>
            <ul className="divide-y divide-border-subtle">
              {keys.map((apiKey) => (
                <ApiKeyRow key={apiKey.id} apiKey={apiKey} onDelete={() => remove(apiKey.id)} disabled={isSaving} />
              ))}
            </ul>
            <p className="py-3 text-xs text-fg-muted">
              {keys.length} / {MAX_API_KEYS} {t.apiKeys.limitCaption}
            </p>
          </>
        );
      })()}

      <Dialog.Root open={isCreateOpen} onOpenChange={(next) => !next && closeCreate()}>
        <Dialog.Content closeLabel={t.apiKeys.cancel}>
          <Dialog.Header>
            <Dialog.Title>{t.apiKeys.create}</Dialog.Title>
            <Dialog.Description>{t.apiKeys.subtitle}</Dialog.Description>
          </Dialog.Header>
          <Dialog.Body>
            <CreateApiKeyForm
              draftName={draftName}
              onNameChange={setDraftName}
              isSaving={isSaving}
              error={error}
              onCancel={closeCreate}
              onSubmit={submit}
            />
          </Dialog.Body>
        </Dialog.Content>
      </Dialog.Root>

      <Dialog.Root open={generated !== null} onOpenChange={(next) => !next && dismissGenerated()}>
        <Dialog.Content closeLabel={t.apiKeys.done}>
          <Dialog.Header>
            <Dialog.Title>{t.apiKeys.revealTitle}</Dialog.Title>
            <Dialog.Description>{generated?.name}</Dialog.Description>
          </Dialog.Header>
          <Dialog.Body>{generated && <RevealKeyPanel generated={generated} onDone={dismissGenerated} />}</Dialog.Body>
        </Dialog.Content>
      </Dialog.Root>
    </SettingsSection>
  );
}
