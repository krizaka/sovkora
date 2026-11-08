"use client";

import React from "react";
import { motion, AnimatePresence, useReducedMotion } from "framer-motion";
import { Icon } from "@krizaka/orazaka-design-system";
import type { TranslationDictionary } from "@/core/context/LocaleContext";

import { cn } from "@krizaka/ui/cn";

interface ChatIndicatorProps {
  attachment: { assetId: string; name: string } | null;
  isUploadingAttachment: boolean;
  onClearAttachment: () => void;
  t: TranslationDictionary;
}

const CHIP_BASE = "inline-flex items-center gap-1.5 pl-2.5 pr-1.5 py-1 rounded-sm text-xs font-semibold border";

/** Spring entrance/exit for a chip; collapses to a fade under reduced-motion. */
function useChipMotion() {
  const reduce = useReducedMotion();
  return {
    initial: reduce ? { opacity: 0 } : { opacity: 0, scale: 0.85, y: 4 },
    animate: { opacity: 1, scale: 1, y: 0 },
    exit: reduce ? { opacity: 0 } : { opacity: 0, scale: 0.85, y: 4 },
    transition: { type: "spring" as const, stiffness: 500, damping: 30, mass: 0.6 },
  };
}

const ClearButton: React.FC<{ onClick: () => void; className: string; label: string }> = ({
  onClick,
  className,
  label,
}) => (
  <motion.button
    type="button"
    onClick={onClick}
    whileTap={{ scale: 0.85 }}
    className={cn("flex h-4 w-4 items-center justify-center rounded-full transition-colors", className)}
    aria-label={label}
  >
    <Icon name="close" size={12} />
  </motion.button>
);

/**
 * Animated context chip above the composer for the staged attachment (the selected
 * capability is shown by the mode selector in the composer itself). The chip springs
 * in on attach and out on clear, mirroring Gemini's composer affordances. Icons come
 * from the shared registry (no emoji), and motion is reduced-motion safe.
 */
export const ChatIndicators: React.FC<ChatIndicatorProps> = ({
  attachment,
  isUploadingAttachment,
  onClearAttachment,
  t,
}) => {
  const chip = useChipMotion();
  const hasContent = attachment || isUploadingAttachment;
  if (!hasContent) return null;

  return (
    <section className="flex flex-wrap items-center gap-2 px-1 border-b border-border-subtle pb-2 mb-1">
      <AnimatePresence mode="popLayout" initial={false}>
        {attachment && (
          <motion.article
            key="attachment"
            layout
            {...chip}
            className={cn(CHIP_BASE, "bg-success/10 text-success border-success/20")}
          >
            <Icon name="attach" size={14} />
            <span className="max-w-[180px] truncate">{attachment.name}</span>
            <ClearButton onClick={onClearAttachment} label={t.chat.clear} className="hover:bg-success/20" />
          </motion.article>
        )}
        {isUploadingAttachment && (
          <motion.span key="uploading" layout {...chip} className="text-xs text-fg-muted px-1">
            {t.chat.uploadingFile}
          </motion.span>
        )}
      </AnimatePresence>
    </section>
  );
};
