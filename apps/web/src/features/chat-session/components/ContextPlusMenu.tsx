import { Button } from "@krizaka/ui/button";
import React, { useRef, useEffect } from "react";
import { Icon, type IconName } from "@krizaka/orazaka-design-system";
import type { ComposerStudio } from "@krizaka/orazaka-shared";
import type { TranslationDictionary } from "@/core/context/LocaleContext";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onExecuteNode: (studio: ComposerStudio) => void;
  studios: ComposerStudio[];
  t: TranslationDictionary;
}

/** Maps a Studio's icon key to a design-system Icon name. */
const ICON_ALIAS: Record<string, IconName> = {
  image: "image",
  video: "video",
  audio: "audio",
  speech: "speech",
  vision: "vision",
  mic: "speech",
  chat: "chat",
};
const studioIcon = (iconKey: string): IconName => ICON_ALIAS[iconKey.toLowerCase()] ?? "spark";

/**
 * A Studio whose one input is an attachment goes under "Analysis".
 *
 * Read off what the Studio declared its input holds, never off a path: this used to be
 * `uriPath.includes("/media/analyze")`, which grouped correctly for the capabilities that
 * existed when it was written and would have put any new pack in the wrong half (ADR-068 §3).
 */
const needsAnAttachment = (studio: ComposerStudio) => studio.inputKind === "ASSET";

/**
 * ContextPlusMenu — the composer "+" popover listing every Studio launchable from a
 * chat bar,
 * grouped into Generation and Analysis, with design-system icons (no emoji) and
 * theme tokens. Selecting a capability stages it (options + optional attachment
 * are then shown above the composer).
 */
export const ContextPlusMenu: React.FC<Props> = ({ isOpen, onClose, onExecuteNode, studios, t }) => {
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        const target = event.target as HTMLElement;
        const button = target.closest("button");
        if (
          button &&
          (button.getAttribute("aria-label")?.toLowerCase().includes("capability") ||
            button.getAttribute("aria-label")?.toLowerCase().includes("fonctionnalité") ||
            button.querySelector("svg")?.classList.contains("rotate-45"))
        ) {
          return;
        }
        onClose();
      }
    };
    if (isOpen) document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const generation = studios.filter((studio) => !needsAnAttachment(studio));
  const analysis = studios.filter(needsAnAttachment);

  const renderGroup = (label: string, group: ComposerStudio[]) => {
    if (group.length === 0) return null;
    return (
      <div className="flex flex-col gap-0.5">
        <div className="px-2.5 pb-1 pt-1.5 text-[10px] font-semibold text-fg-muted">{label}</div>
        {group.map((studio) => {
          const unavailable = !studio.available;
          return (
            <Button
              key={studio.studioKey}
              variant="ghost"
              disabled={unavailable}
              title={unavailable ? studio.lockedReason : undefined}
              aria-disabled={unavailable}
              onClick={() => {
                if (unavailable) return;
                onExecuteNode(studio);
                onClose();
              }}
              className="h-auto w-full justify-start gap-3 px-2.5 py-2 text-left font-medium"
            >
              <span className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-lg bg-accent-soft text-fg-accent">
                <Icon name={studioIcon(studio.iconKey)} size={15} />
              </span>
              <span className="flex-1">{studio.label}</span>
              {unavailable && (
                <span className="text-[10px] font-semibold text-fg-muted">{t.operationGraph.stateLocked}</span>
              )}
            </Button>
          );
        })}
      </div>
    );
  };

  return (
    <div
      ref={menuRef}
      role="menu"
      className="absolute bottom-16 left-4 z-40 flex w-72 flex-col gap-1 rounded-2xl border border-border-default bg-surface-1/94 p-2 shadow-xl animate-in slide-in-from-bottom-2 fade-in duration-200"
    >
      {studios.length === 0 ? (
        <div className="px-3 py-3 text-xs italic text-fg-muted">{t.chat.noActiveConversation}</div>
      ) : (
        <>
          {renderGroup(t.chat.capGeneration, generation)}
          {renderGroup(t.chat.capAnalysis, analysis)}
        </>
      )}
    </div>
  );
};
