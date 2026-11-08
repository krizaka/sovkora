"use client";

import React from "react";
import { Dialog } from "@krizaka/ui/dialog";
import { ThreadList } from "./ThreadList";
import { ChatThread } from "@/core/types/chat.types";

interface ChatDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  threads: ChatThread[];
  activeConversationId: string;
  onSelectThread: (id: string) => void;
  isLoadingThreads: boolean;
  onCreateThread: () => void;
  onDeleteThread: (id: string) => void;
  t: {
    chat: { memoryBlocks: string };
    a11y: { closeHistory: string };
  };
}

/**
 * The conversations on a phone: the @krizaka/ui dialog as a side panel (Radix: focus trap, Escape, a tap outside,
 * focus return). @krizaka/ui places a panel on the right only; this one keeps its 1.x place on the left through
 * `className` — a `left` placement is requested upstream (krizaka/krizaka-ui#43).
 */
export function ChatDrawer({
  isOpen,
  onClose,
  threads,
  activeConversationId,
  onSelectThread,
  isLoadingThreads,
  onCreateThread,
  onDeleteThread,
  t,
}: Readonly<ChatDrawerProps>) {
  return (
    <Dialog.Root open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Content
        placement="right"
        closeLabel={t.a11y.closeHistory}
        aria-describedby={undefined}
        className="left-0 right-auto w-72 max-w-[80vw] border-l-0 border-r bg-surface-1/95 md:hidden"
      >
        <Dialog.Header className="border-b border-border-subtle/60 pb-4">
          <Dialog.Title className="text-sm font-semibold">{t.chat.memoryBlocks}</Dialog.Title>
        </Dialog.Header>
        <Dialog.Body className="px-0 pb-0">
          <ThreadList
            threads={threads}
            activeId={activeConversationId}
            onSelectThread={(id) => {
              onSelectThread(id);
              onClose();
            }}
            isLoading={isLoadingThreads}
            onCreateThread={() => {
              onCreateThread();
              onClose();
            }}
            onDeleteThread={onDeleteThread}
          />
        </Dialog.Body>
      </Dialog.Content>
    </Dialog.Root>
  );
}
