/* eslint-disable no-restricted-syntax */
"use client";

import { Button } from "@krizaka/ui/button";
import React from "react";
import { format } from "date-fns";
import { Icon, SentinelMini } from "@krizaka/orazaka-design-system";
import { ChatMessage } from "@/core/types/chat.types";
import { useTranslation } from "@/core/context/LocaleContext";

import { cn } from "@krizaka/ui/cn";

interface Props {
  message: ChatMessage;
  /** Stagger index for slide-in animation delay */
  index?: number;
}

/**
 * Individual chat message bubble — Calm Obsidian 2026 design.
 *
 * User messages align right with accent-tinted surface.
 * AI messages align left with surface-1 background.
 * Includes hover-to-copy action bar and staggered slide-in.
 */
export const MessageBubble: React.FC<Props> = ({ message, index = 0 }) => {
  const isUser = message.role === "user";
  const { t } = useTranslation();
  const [copied, setCopied] = React.useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(message.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard API not available
    }
  };

  return (
    <article
      className={cn("flex w-full mb-4 group/msg", isUser ? "justify-end" : "justify-start")}
      style={{
        animationDelay: `${Math.min(index * 50, 300)}ms`,
        animationFillMode: "backwards",
      }}
    >
      <section
        className={cn(
          "flex max-w-[75%] gap-3 items-start animate-in fade-in duration-300",
          isUser ? "flex-row-reverse slide-in-from-right-3" : "flex-row slide-in-from-left-3",
        )}
        style={{
          animationDelay: `${Math.min(index * 50, 300)}ms`,
          animationFillMode: "backwards",
        }}
      >
        {/* Avatar */}
        <figure
          className={cn(
            "h-9 w-9 rounded-2xl flex items-center justify-center text-[11px] font-bold border select-none flex-shrink-0 transition-colors duration-200",
            isUser
              ? "bg-accent-soft border-accent/20 text-accent"
              : "bg-surface-2 border-border-subtle text-fg-secondary",
          )}
        >
          {isUser ? "U" : <SentinelMini size={18} />}
        </figure>

        {/* Text bubble + actions */}
        <section className="relative">
          <section
            className={cn(
              "p-4 rounded-2xl border text-[13px] leading-[1.7] text-fg transition-shadow duration-200",
              isUser
                ? "bg-accent-soft border-accent/10 rounded-tr-md"
                : "bg-surface-1 border-border-subtle rounded-tl-md",
            )}
          >
            <p className="whitespace-pre-wrap">{message.content}</p>
            <span className="text-[10px] text-fg-muted block mt-2 text-right select-none tabular-nums">
              {format(message.timestamp, "HH:mm")}
            </span>
          </section>

          {/* Hover action bar */}
          <section
            className={cn(
              "absolute -bottom-3 opacity-0 group-hover/msg:opacity-100 transition-all duration-200 flex items-center gap-1",
              isUser ? "right-0" : "left-0",
            )}
          >
            <Button
              variant="secondary"
              size="sm"
              className="h-7 gap-1 px-2 text-[11px] shadow-sm"
              onClick={handleCopy}
              aria-label={t.chat.copyMessage}
            >
              {copied ? <Icon name="check" size={12} className="text-fg-success" /> : <Icon name="copy" size={12} />}
              {copied ? t.chat.copiedMessage : t.chat.copyMessage}
            </Button>
          </section>
        </section>
      </section>
    </article>
  );
};
