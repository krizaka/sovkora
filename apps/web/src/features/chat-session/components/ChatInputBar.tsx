"use client";

import React from "react";
import { Icon, IconButton, Button } from "@krizaka/orazaka-design-system";
import { ChevronDownIcon, CloseIcon, ShieldIcon } from "@krizaka/icons";
import { MessageComposer } from "@krizaka/ui/chat";
import { cn } from "@krizaka/ui/cn";
import { ContextPlusMenu } from "./ContextPlusMenu";
import { ChatIndicators } from "./ChatIndicators";
import { CapabilityOptions } from "./CapabilityOptions";
import { resolveModelCategory } from "@/core/constants/capability.constants";
import { routingStatus } from "@/core/utils/routing";
import type { ChatInputBarProps } from "@/features/chat-session/components/chatInputBar.types";

// Attributes for the kit's field and send button (the e2e and the unit tests find them by these ids).
const INPUT_ATTRS: Record<string, string> = {
  "data-testid": "chat-input",
  "data-composer-field": "",
  autoComplete: "off",
};
const SEND_ATTRS: Record<string, string> = { "data-testid": "chat-submit" };

export const ChatInputBar: React.FC<ChatInputBarProps> = ({
  input,
  onInputChange,
  onSend,
  isSending,
  isGenerating,
  isUploadingAttachment,
  selectedStudio,
  selectedOptions,
  onOptionsChange,
  attachment,
  onClearStudio,
  onClearAttachment,
  isPlusMenuOpen,
  onTogglePlusMenu,
  onClosePlusMenu,
  onExecuteNode,
  composerStudios,
  fileInputRef,
  onFileChange,
  routing,
  t,
  isCentered = false,
}) => {
  // While the agent answers, the send is held — the field, its chip and its box stay exactly where they are.
  const isAgentBusy = isSending || isGenerating;

  /** Analysis capabilities require a staged asset before they can run. */
  const needsAttachment = !!selectedStudio && selectedStudio.inputKind === "ASSET";
  const showOptions = !!selectedStudio && resolveModelCategory(selectedStudio.capabilityKey) !== null;

  const canSend =
    !isUploadingAttachment && !(needsAttachment && !attachment) && (!!input.trim() || !!attachment || !!selectedStudio);

  // The status line says only what is true: local only when the last routing was local, plus the estimate.
  const status = routingStatus(routing);
  const statusParts: React.ReactElement[] = [];
  if (isAgentBusy) {
    statusParts.push(<span key="busy">{t.chat.composerBusy}</span>);
  } else {
    if (status.localOnly) {
      statusParts.push(
        <span key="local" className="inline-flex items-center gap-1.5">
          <ShieldIcon size={14} className="text-success" />
          {t.chat.localBadge}
        </span>,
      );
    } else if (status.routedTo) {
      statusParts.push(<span key="routed">{t.chat.statusRoutedTo.replace("{provider}", status.routedTo)}</span>);
    }
    if (status.credits !== null) {
      statusParts.push(<span key="credits">{t.chat.statusCredits.replace("{credits}", String(status.credits))}</span>);
    }
  }

  return (
    <section
      data-testid="chat-composer"
      className={cn(
        "relative z-25 flex w-full flex-col items-center bg-transparent",
        isCentered ? "mt-6" : "px-4 pb-4 pt-0 md:pb-6",
      )}
    >
      <article className={cn("flex w-full flex-col gap-2", isCentered ? "max-w-2xl px-6" : "max-w-3xl")}>
        <ChatIndicators
          attachment={attachment}
          isUploadingAttachment={isUploadingAttachment}
          onClearAttachment={onClearAttachment}
          t={t}
        />

        {showOptions && selectedStudio && (
          <CapabilityOptions
            studio={selectedStudio}
            options={selectedOptions}
            onChange={onOptionsChange}
            attachment={attachment}
            onAttach={() => fileInputRef.current?.click()}
            t={t}
          />
        )}

        <section className="relative">
          <ContextPlusMenu
            isOpen={isPlusMenuOpen}
            onClose={onClosePlusMenu}
            onExecuteNode={onExecuteNode}
            studios={composerStudios}
            t={t}
          />
          <input type="file" ref={fileInputRef} className="hidden" onChange={onFileChange} />
          <MessageComposer
            variant="pill"
            value={input}
            onValueChange={onInputChange}
            onSend={onSend}
            busy={isAgentBusy}
            canSend={canSend}
            placeholder={t.chat.typeMessage}
            sendLabel={t.chat.send}
            maxLength={20000}
            inputProps={INPUT_ATTRS}
            sendProps={SEND_ATTRS}
            leading={
              <>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  shape="pill"
                  onClick={onTogglePlusMenu}
                  disabled={isAgentBusy}
                  aria-label={t.chat.addCapability}
                  aria-expanded={isPlusMenuOpen}
                  className={cn(
                    "h-8 gap-1.5 pl-2.5 pr-2 font-medium",
                    selectedStudio && "bg-accent-soft text-fg-accent",
                  )}
                >
                  <Icon name="spark" size={15} />
                  {/* On a phone the chip is its icon and chevron: the field keeps the room to write. */}
                  <span className="hidden max-w-[96px] truncate sm:inline">
                    {selectedStudio ? selectedStudio.label : t.chat.modeChat}
                  </span>
                  <ChevronDownIcon
                    size={14}
                    className={cn("transition-transform duration-200", isPlusMenuOpen && "rotate-180")}
                  />
                </Button>
                {selectedStudio && (
                  <IconButton
                    variant="ghost"
                    size="sm"
                    shape="pill"
                    onClick={onClearStudio}
                    disabled={isAgentBusy}
                    label={t.chat.clear}
                  >
                    <CloseIcon size={13} />
                  </IconButton>
                )}
                <IconButton
                  variant="ghost"
                  size="sm"
                  shape="pill"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={isAgentBusy}
                  label={t.a11y.attachFile}
                >
                  <Icon name="attach" size={18} />
                </IconButton>
              </>
            }
            status={
              statusParts.length
                ? statusParts.flatMap((p, i) =>
                    i
                      ? [
                          <span key={`sep-${p.key}`} aria-hidden>
                            ·
                          </span>,
                          p,
                        ]
                      : [p],
                  )
                : undefined
            }
          />
        </section>
      </article>
    </section>
  );
};
