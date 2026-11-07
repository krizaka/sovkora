"use client";

import * as React from "react";
import { Button } from "@krizaka/ui/button";
import { Dialog } from "@krizaka/ui/dialog";
import { useTranslation } from "@/core/context/LocaleContext";

/** What a webhook trigger shows once: the address to call and the secret that signs the call. */
export interface RevealedWebhook {
  url: string;
  secret: string;
}

/** One value with a copy button: the value is selectable too, in case the clipboard is refused. */
function CopyField({ label, value, testId }: Readonly<{ label: string; value: string; testId: string }>) {
  const { t } = useTranslation();
  const [copied, setCopied] = React.useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
    } catch {
      // The value stays selectable.
    }
  };
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium text-fg-secondary">{label}</p>
      <div className="flex items-center gap-2">
        <code
          data-testid={testId}
          className="min-w-0 flex-1 break-all rounded-md border border-border-subtle bg-surface-2 px-3 py-2 font-mono text-xs text-fg"
        >
          {value}
        </code>
        <Button type="button" variant="secondary" size="sm" onClick={copy}>
          {copied ? t.automations.secret.copied : t.automations.secret.copy}
        </Button>
      </div>
    </div>
  );
}

/**
 * The webhook secret, shown once (on create and on rotate), with the address to call. Closing it drops the secret from
 * memory: nothing reads it back, the API has no way to.
 */
export function SecretDialog({ revealed, onDone }: Readonly<{ revealed: RevealedWebhook | null; onDone: () => void }>) {
  const { t } = useTranslation();
  const s = t.automations.secret;
  return (
    <Dialog.Root open={revealed !== null} onOpenChange={(next) => !next && onDone()}>
      <Dialog.Content closeLabel={s.close}>
        <Dialog.Header>
          <Dialog.Title>{s.title}</Dialog.Title>
          <Dialog.Description>{s.desc}</Dialog.Description>
        </Dialog.Header>
        <Dialog.Body className="space-y-4">
          {revealed && (
            <>
              <CopyField label={s.urlLabel} value={revealed.url} testId="webhook-url" />
              <CopyField label={s.secretLabel} value={revealed.secret} testId="webhook-secret" />
              <p className="text-xs text-fg-muted">{s.verify}</p>
            </>
          )}
        </Dialog.Body>
        <Dialog.Footer>
          <Button type="button" size="sm" onClick={onDone}>
            {s.done}
          </Button>
        </Dialog.Footer>
      </Dialog.Content>
    </Dialog.Root>
  );
}
