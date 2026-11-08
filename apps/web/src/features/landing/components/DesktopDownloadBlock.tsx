"use client";

import { DownloadIcon, ExternalIcon } from "@krizaka/icons";
import { Button } from "@krizaka/ui/button";
import { useTranslation } from "@/core/context/LocaleContext";
import { CopyButton } from "@/features/landing/components/CopyButton";
import { fill } from "@/core/context/fill";
import { RELEASES_URL, type DesktopRelease } from "@/features/landing/utils/release";

export type DesktopDownload = (DesktopRelease & { sha256: string | null }) | null;

const megabytes = (bytes: number) => `${(bytes / 1_000_000).toFixed(1)} MB`;
export function DownloadBlock({
  release,
  downloadEnabled,
}: Readonly<{ release: DesktopDownload; downloadEnabled: boolean }>) {
  const { t } = useTranslation();
  const d = t.desktop;
  if (!downloadEnabled) {
    return (
      <div className="space-y-3">
        <Button variant="primary" size="lg" className="font-bold" disabled>
          <DownloadIcon size={18} />
          {d.download.button}
        </Button>
        <p role="note" className="text-sm text-fg-secondary">
          {d.download.disabled}
        </p>
      </div>
    );
  }
  if (release) {
    return (
      <div className="space-y-3">
        <Button asChild variant="primary" size="lg" className="font-bold">
          <a href="/desktop/download">
            <DownloadIcon size={18} />
            {d.download.button}
          </a>
        </Button>
        <p className="text-sm text-fg-secondary">
          {fill(d.download.facts, { size: megabytes(release.dmg.bytes) })} ·{" "}
          <span data-testid="desktop-version">{fill(d.download.version, { version: release.version })}</span>
        </p>
        <p role="note" className="text-sm text-fg-secondary">
          {d.download.testBuild}
        </p>
        {release.sha256 && (
          <details className="group text-sm">
            <summary className="cursor-pointer font-medium text-fg-accent">{d.download.verify}</summary>
            <div className="mt-2 space-y-2 text-fg-secondary">
              <p>{d.download.verifyBody}</p>
              <p className="flex flex-wrap items-center gap-2">
                <code data-testid="desktop-sha" className="break-all font-mono text-xs text-fg">
                  {release.sha256}
                </code>
                <CopyButton
                  text={release.sha256}
                  label={d.download.shaCopy}
                  done={d.download.shaCopied}
                  failed={d.download.shaCopyFailed}
                />
              </p>
            </div>
          </details>
        )}
      </div>
    );
  }
  return (
    <div className="flex flex-wrap items-center gap-4">
      <p className="text-sm text-fg-secondary">{d.download.unreadable}</p>
      <Button asChild variant="secondary" size="md" shape="pill">
        <a href={RELEASES_URL} target="_blank" rel="noopener noreferrer">
          {d.download.releases}
          <ExternalIcon size={16} />
        </a>
      </Button>
    </div>
  );
}
