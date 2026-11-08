"use client";

import { useEffect, useRef, useState } from "react";
import {
  Dropzone,
  UploadList,
  type FileRejection,
  type UploadItem,
  type UploadListLabels,
} from "@krizaka/ui/file-upload";
import { useTranslation } from "@/core/context/LocaleContext";
import { fill } from "@/core/context/fill";
import { MediaApi } from "@/services/media.api";
import type { AssetKind } from "@/features/studio/hooks/useInputSchema";

const MB = 1024 * 1024;

/** What each kind accepts and how large it may be: the same rules the field says in words. */
export const ASSET_RULES: Record<AssetKind, { accept: string; maxSize: number }> = {
  image: { accept: "image/jpeg,image/png,image/webp", maxSize: 20 * MB },
  video: { accept: "video/mp4,video/quicktime,video/webm", maxSize: 500 * MB },
  audio: { accept: "audio/mpeg,audio/wav,audio/x-wav,audio/mp4,audio/x-m4a,audio/aac", maxSize: 100 * MB },
};

interface AssetInputProps {
  id: string;
  kind: AssetKind;
  /** The asset id bound to the form ("" when none). */
  value: string;
  disabled: boolean;
  onChange: (assetId: string) => void;
  /** True while a file is being sent: the form cannot be submitted meanwhile. */
  onBusyChange: (busy: boolean) => void;
  describedBy?: string;
  /** The field's name, for assistive technology. */
  label: string;
}

/**
 * A Studio input that takes an uploaded file. The user picks (or drops, or on a phone takes) a file; it goes
 * through the authenticated upload route (`/api/v1/media/upload`, BFF) and the asset id it returns is what the
 * form submits — an asset id is never typed. The file is previewed, can be removed, and a failed upload can be
 * tried again.
 */
export function AssetInput({
  id,
  kind,
  value,
  disabled,
  onChange,
  onBusyChange,
  describedBy,
  label,
}: Readonly<AssetInputProps>) {
  const { t, locale } = useTranslation();
  const [item, setItem] = useState<UploadItem | null>(null);
  const [refusal, setRefusal] = useState<string | null>(null);
  const fileRef = useRef<File | null>(null);
  const attempt = useRef(0);
  const rules = ASSET_RULES[kind];
  const words = {
    image: [t.studio.asset.imageTitle, t.studio.asset.imageBrowse, t.studio.asset.imageHint],
    video: [t.studio.asset.videoTitle, t.studio.asset.videoBrowse, t.studio.asset.videoHint],
    audio: [t.studio.asset.audioTitle, t.studio.asset.audioBrowse, t.studio.asset.audioHint],
  }[kind];

  // The preview is an object URL: free it when it goes.
  const previewUrl = item?.previewUrl;
  useEffect(
    () => () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    },
    [previewUrl],
  );
  // The form's callback changes on every render; the field only needs the latest one, and to release the form on unmount.
  const busyRef = useRef(onBusyChange);
  useEffect(() => {
    busyRef.current = onBusyChange;
  });
  useEffect(() => () => busyRef.current(false), []);

  const send = async (file: File, previewUrlOf: string | undefined) => {
    const mine = ++attempt.current;
    fileRef.current = file;
    setRefusal(null);
    onChange("");
    onBusyChange(true);
    setItem({
      id,
      name: file.name,
      size: file.size,
      type: file.type,
      progress: 0,
      status: "uploading",
      previewUrl: previewUrlOf,
    });
    try {
      const { assetId } = await MediaApi.uploadMedia(file);
      if (mine !== attempt.current) return;
      onChange(assetId);
      setItem((current) => (current ? { ...current, progress: 1, status: "done" } : current));
    } catch {
      if (mine !== attempt.current) return;
      setItem((current) => (current ? { ...current, status: "error", error: t.studio.asset.uploadFailed } : current));
    } finally {
      if (mine === attempt.current) onBusyChange(false);
    }
  };

  const remove = () => {
    attempt.current++;
    fileRef.current = null;
    setItem(null);
    setRefusal(null);
    onChange("");
    onBusyChange(false);
  };

  const refuse = (rejections: FileRejection[]) => {
    const first = rejections[0];
    if (!first) return;
    const message = first.reason.startsWith("size") ? t.studio.asset.tooLarge : t.studio.asset.wrongType;
    setRefusal(fill(message, { name: first.file.name }));
  };

  const labels: Partial<UploadListLabels> = {
    list: t.studio.asset.list,
    retry: (name) => fill(t.studio.asset.retry, { name }),
    cancel: (name) => fill(t.studio.asset.cancel, { name }),
    remove: (name) => fill(t.studio.asset.remove, { name }),
    status: (status) =>
      ({ done: t.studio.asset.done, error: t.studio.asset.error })[status as "done" | "error"] ??
      t.studio.asset.uploading,
  };

  return (
    <fieldset
      id={id}
      aria-label={label}
      aria-describedby={describedBy}
      className="m-0 flex min-w-0 flex-col gap-2 border-0 p-0"
      data-asset-kind={kind}
      data-asset-id={value || undefined}
    >
      {item ? (
        <UploadList
          items={[item]}
          locale={locale}
          labels={labels}
          onCancel={remove}
          onRemove={remove}
          onRetry={() => fileRef.current && void send(fileRef.current, item.previewUrl)}
        />
      ) : (
        <Dropzone
          title={words[0]}
          browseLabel={words[1]}
          hint={words[2]}
          accept={rules.accept}
          maxSize={rules.maxSize}
          disabled={disabled}
          onFiles={([file]) => void send(file, URL.createObjectURL(file))}
          onReject={refuse}
        />
      )}
      {refusal && (
        <p role="alert" className="text-xs text-fg-danger">
          {refusal}
        </p>
      )}
    </fieldset>
  );
}
