"use client";

import { useEffect, useState } from "react";
import { Button } from "@krizaka/ui/button";
import { Dialog } from "@krizaka/ui/dialog";
import { useTranslation } from "@/core/context/LocaleContext";
import { useRegulatedPack } from "@/features/studio/consent/useRegulated";
import { StudioApi } from "@/services/studio.api";
import type { Installation } from "@krizaka/orazaka-shared";

/** The confirmation outlives the reload the erase triggers (the page remounts): remembered, then forgotten. */
const ERASED_KEY = "orazaka_wellbeing_erased";
const ERASED_NOTE_MS = 15_000;

function recentlyErased(): boolean {
  try {
    return sessionStorage.getItem(ERASED_KEY) === "1";
  } catch {
    return false;
  }
}

function rememberErased(on: boolean) {
  try {
    if (on) sessionStorage.setItem(ERASED_KEY, "1");
    else sessionStorage.removeItem(ERASED_KEY);
  } catch {
    // The note is a courtesy; the erase already happened.
  }
}

interface ConsentNoticeProps {
  studioKey: string;
  installation: Installation | undefined;
  onChanged: () => void;
}

/**
 * What a wellbeing (REGULATED) Studio's page says about consent, and the way out.
 *
 * Before the first run: a plain note that consent will be asked. Once installed (the install is where the service
 * records the consent, its version, the age attestation and the region): that the person agreed, and « Withdraw my
 * consent ». Withdrawing uninstalls: the Studio stops at once and agreeing again is asked at the next run, because
 * the service refuses any install without it. « Delete all my wellbeing data » is the erase the statement promises:
 * a confirmation, then the service deletes entries, files, installations and consent for real (orazaka#301).
 */
export function ConsentNotice({ studioKey, installation, onChanged }: Readonly<ConsentNoticeProps>) {
  const { t } = useTranslation();
  const pack = useRegulatedPack(studioKey);
  const [asking, setAsking] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState<string | null>(null);
  const [erasing, setErasing] = useState(false);
  const [erased, setErased] = useState(recentlyErased);

  // The note goes away by itself; the timer, not an unmount, forgets it (the reload remounts this component).
  useEffect(() => {
    if (!erased) return;
    const timer = setTimeout(() => {
      rememberErased(false);
      setErased(false);
    }, ERASED_NOTE_MS);
    return () => clearTimeout(timer);
  }, [erased]);

  if (!pack) return null;

  const eraseAll = async () => {
    setBusy(true);
    setFailed(null);
    try {
      await StudioApi.eraseWellbeingData(pack);
      setErasing(false);
      setErased(true);
      rememberErased(true);
      onChanged();
    } catch (caught) {
      setFailed(caught instanceof Error ? caught.message : t.studio.loadError);
    } finally {
      setBusy(false);
    }
  };

  const withdraw = async () => {
    if (!installation) return;
    setBusy(true);
    setFailed(null);
    try {
      await StudioApi.uninstall(installation.id);
      setAsking(false);
      onChanged();
    } catch (caught) {
      setFailed(caught instanceof Error ? caught.message : t.studio.loadError);
    } finally {
      setBusy(false);
    }
  };

  if (erased) {
    return <output className="block max-w-2xl text-xs text-fg-secondary">{t.consent.erased}</output>;
  }

  if (!installation || installation.status === "REVOKED") {
    return <p className="max-w-2xl text-xs text-fg-secondary">{t.consent.asked}</p>;
  }

  return (
    <div className="flex flex-wrap items-center gap-3 text-xs text-fg-secondary">
      <span>{t.consent.givenTitle}</span>
      <Button variant="ghost" size="sm" onClick={() => setAsking(true)}>
        {t.consent.withdraw}
      </Button>
      <Button variant="ghost" size="sm" onClick={() => setErasing(true)}>
        {t.consent.erase}
      </Button>
      <Dialog.Root open={erasing} onOpenChange={(next) => !next && !busy && setErasing(false)}>
        <Dialog.Content closeLabel={t.consent.close}>
          <Dialog.Header>
            <Dialog.Title>{t.consent.eraseTitle}</Dialog.Title>
            <Dialog.Description>{t.consent.eraseBody}</Dialog.Description>
          </Dialog.Header>
          {failed && (
            <Dialog.Body>
              <p role="alert" className="text-xs text-fg-danger">
                {failed}
              </p>
            </Dialog.Body>
          )}
          <Dialog.Footer>
            <Button variant="ghost" size="sm" onClick={() => setErasing(false)} disabled={busy}>
              {t.consent.keep}
            </Button>
            <Button size="sm" onClick={() => void eraseAll()} loading={busy}>
              {busy ? t.consent.erasing : t.consent.eraseConfirm}
            </Button>
          </Dialog.Footer>
        </Dialog.Content>
      </Dialog.Root>
      <Dialog.Root open={asking} onOpenChange={(next) => !next && !busy && setAsking(false)}>
        <Dialog.Content closeLabel={t.consent.close}>
          <Dialog.Header>
            <Dialog.Title>{t.consent.withdrawTitle}</Dialog.Title>
            <Dialog.Description>{t.consent.withdrawBody}</Dialog.Description>
          </Dialog.Header>
          {failed && (
            <Dialog.Body>
              <p role="alert" className="text-xs text-fg-danger">
                {failed}
              </p>
            </Dialog.Body>
          )}
          <Dialog.Footer>
            <Button variant="ghost" size="sm" onClick={() => setAsking(false)} disabled={busy}>
              {t.consent.keep}
            </Button>
            <Button size="sm" onClick={() => void withdraw()} loading={busy}>
              {busy ? t.consent.withdrawing : t.consent.withdrawConfirm}
            </Button>
          </Dialog.Footer>
        </Dialog.Content>
      </Dialog.Root>
    </div>
  );
}
