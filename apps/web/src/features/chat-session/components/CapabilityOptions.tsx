"use client";

import { Button } from "@krizaka/ui/button";
import * as React from "react";
import { useQuery } from "@krizaka/query";
import { getSession } from "next-auth/react";
import { Icon } from "@krizaka/orazaka-design-system";
import type { ComposerStudio } from "@krizaka/orazaka-shared";
import type { ComposerRunOptions as Options } from "@/features/chat-session/utils/startComposerRun";
import {
  resolveModelCategory,
  FALLBACK_MODELS,
  SPEECH_VOICES,
  IMAGE_SIZES,
  VIDEO_DURATIONS,
  MODEL_CATEGORY,
} from "@/core/constants/capability.constants";
import type { TranslationDictionary } from "@/core/context/LocaleContext";
import { CapabilityField } from "@/features/chat-session/components/CapabilityOptionsParts";
import type { ModelOption } from "@/features/chat-session/components/CapabilityOptionsParts";

import { cn } from "@krizaka/ui/cn";

interface CapabilityOptionsProps {
  studio: ComposerStudio;
  options: Options;
  onChange: (patch: Partial<Options>) => void;
  attachment: { assetId: string; name: string } | null;
  onAttach: () => void;
  t: TranslationDictionary;
}

/**
 * CapabilityOptions — per-capability controls shown above the composer when a
 * capability is selected: a model dropdown (catalog-by-category → FALLBACK_MODELS)
 * plus capability-specific fields (voice / size / duration). Analysis capabilities
 * additionally require a staged asset and surface an attach affordance.
 */
export function CapabilityOptions({
  studio,
  options,
  onChange,
  attachment,
  onAttach,
  t,
}: Readonly<CapabilityOptionsProps>) {
  const category = resolveModelCategory(studio.capabilityKey);
  // Declared by the pack, not read off a path: the Studio says its one input holds an
  // asset, and the attach affordance follows that (ADR-068 §3).
  const needsAttachment = studio.inputKind === "ASSET";
  const showVoice = category === MODEL_CATEGORY.SPEECH || (category === MODEL_CATEGORY.AUDIO && !needsAttachment);
  const showSize = category === MODEL_CATEGORY.IMAGE && !needsAttachment;
  const showDuration = category === MODEL_CATEGORY.VIDEO && !needsAttachment;

  // The catalogue's models of this capability; the fixed list stands in until it answers or when it has none.
  const catalog = useQuery({
    queryKey: ["models-catalog", category],
    enabled: Boolean(category),
    retry: false,
    queryFn: async () => {
      const session = await getSession();
      const res = await fetch("/api/v1/models/catalog", {
        headers: session?.user?.id ? { Authorization: `Bearer ${session.user.id}` } : {},
      });
      if (!res.ok) throw new Error("catalog unavailable");
      const data: {
        modelName: string;
        modelLabel: string;
        category: string;
        compatible?: boolean;
        incompatibleReason?: string | null;
        description?: string | null;
        requiresReferenceImage?: boolean;
      }[] = await res.json();
      return data
        .filter((m) => m.category === category)
        .map((m) => ({
          value: m.modelName,
          label: m.modelLabel,
          compatible: m.compatible !== false,
          reason: m.incompatibleReason,
          description: m.description,
          requiresReferenceImage: m.requiresReferenceImage,
        }));
    },
  });
  const models: ModelOption[] = React.useMemo(() => {
    if (!category) return [];
    if (catalog.data && catalog.data.length > 0) return catalog.data;
    if (catalog.isPending) return [];
    return (FALLBACK_MODELS[category] ?? []).map((m) => ({ value: m, label: m, compatible: true }));
  }, [category, catalog.data, catalog.isPending]);

  if (!category) return null;

  const selectedModel = models.find((m) => m.value === options.model);

  return (
    <section className="flex flex-col gap-2.5 border-b border-border-subtle pb-2.5">
      <div className="flex flex-wrap items-end gap-2.5">
        <CapabilityField
          label={t.chat.optModel}
          value={options.model ?? ""}
          onChange={(e) => onChange({ model: e.target.value || undefined })}
        >
          <option value="">{t.chat.optAuto}</option>
          {models.map((m) => (
            <option key={m.value} value={m.value} disabled={!m.compatible} title={m.reason ?? undefined}>
              {m.compatible ? m.label : `${m.label} (incompatible)`}
            </option>
          ))}
        </CapabilityField>

        {showVoice && (
          <CapabilityField
            label={t.chat.optVoice}
            value={options.voice ?? ""}
            onChange={(e) => onChange({ voice: e.target.value || undefined })}
          >
            <option value="">{t.chat.optAuto}</option>
            {SPEECH_VOICES.map((v) => (
              <option key={v} value={v}>
                {v}
              </option>
            ))}
          </CapabilityField>
        )}

        {showSize && (
          <CapabilityField
            label={t.chat.optSize}
            value={options.size ?? ""}
            onChange={(e) => onChange({ size: e.target.value || undefined })}
          >
            <option value="">{t.chat.optAuto}</option>
            {IMAGE_SIZES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </CapabilityField>
        )}

        {showDuration && (
          <CapabilityField
            label={t.chat.optDuration}
            value={options.durationSeconds != null ? String(options.durationSeconds) : ""}
            onChange={(e) =>
              onChange({
                durationSeconds: e.target.value ? Number(e.target.value) : undefined,
              })
            }
          >
            <option value="">{t.chat.optAuto}</option>
            {VIDEO_DURATIONS.map((d) => (
              <option key={d} value={d}>
                {d}s
              </option>
            ))}
          </CapabilityField>
        )}
      </div>

      {selectedModel?.description && (
        <p className="text-[11px] leading-snug text-fg-muted">{selectedModel.description}</p>
      )}

      {needsAttachment && (
        <Button
          variant="outline"
          size="sm"
          onClick={onAttach}
          className={cn("w-fit", attachment ? "border-accent/40 bg-accent-soft text-fg-accent" : "border-dashed")}
        >
          <Icon name={attachment ? "check" : "attach"} size={14} />
          <span className="max-w-[200px] truncate">{attachment ? attachment.name : t.chat.attachToAnalyze}</span>
        </Button>
      )}

      {selectedModel?.requiresReferenceImage && !needsAttachment && (
        <Button
          variant="outline"
          size="sm"
          onClick={onAttach}
          className={cn("w-fit", attachment ? "border-accent/40 bg-accent-soft text-fg-accent" : "border-dashed")}
        >
          <Icon name={attachment ? "check" : "image"} size={14} />
          <span className="max-w-[200px] truncate">{attachment ? attachment.name : t.chat.attachReferenceImage}</span>
        </Button>
      )}
    </section>
  );
}
