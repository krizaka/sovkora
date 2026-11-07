import React from "react";
import { format } from "date-fns";
import { ChatThread } from "@/core/types/chat.types";
import { useTranslation } from "@/core/context/LocaleContext";

import { PlusIcon, TrashIcon } from "@krizaka/icons";
import { Button, IconButton } from "@krizaka/ui/button";
import { cn } from "@krizaka/ui/cn";

interface Props {
  threads: ChatThread[];
  activeId: string;
  onSelectThread: (id: string) => void;
  isLoading: boolean;
  onCreateThread: () => void;
  onDeleteThread: (id: string) => void;
}

/**
 * ThreadList — sidebar panel listing active chat threads.
 * Calm Obsidian 2026 design: solid surfaces, no backdrop-blur, no hover:scale.
 */
export const ThreadList: React.FC<Props> = ({
  threads,
  activeId,
  onSelectThread,
  isLoading,
  onCreateThread,
  onDeleteThread,
}) => {
  const { t } = useTranslation();

  if (isLoading) {
    return (
      <div className="space-y-2 p-4">
        {[1, 2, 3].map((n) => (
          <div key={n} className="h-12 bg-surface-2 rounded-lg" />
        ))}
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full bg-surface-1 border-r border-border-subtle">
      <div className="p-4 border-b border-border-subtle flex items-center justify-between">
        <h2 className="text-sm font-semibold text-fg">{t.chat.memoryBlocks}</h2>
        <Button id="btn-new-block" variant="secondary" size="sm" onClick={onCreateThread}>
          <PlusIcon size={14} />
          {t.chat.newBlock}
        </Button>
      </div>
      <div className="flex-1 overflow-y-auto p-2 space-y-1">
        {threads.map((thread) => {
          const isActive = thread.conversationId === activeId;
          return (
            <div
              key={thread.conversationId}
              className={cn(
                "group w-full relative flex items-center justify-between rounded-lg transition-colors duration-150 text-sm border",
                isActive
                  ? "bg-surface-2 border-border-default text-fg font-semibold"
                  : "bg-transparent border-transparent text-fg-secondary hover:bg-surface-2",
              )}
            >
              <Button
                variant="ghost"
                onClick={() => onSelectThread(thread.conversationId)}
                className="h-auto min-w-0 flex-1 flex-col items-start gap-1 p-3 text-left font-normal hover:bg-transparent"
              >
                <span className="w-full truncate pr-6">{thread.title}</span>
                <span className="text-xs text-fg-muted">{format(thread.updatedAt, "yyyy-MM-dd")}</span>
              </Button>
              <IconButton
                variant="ghost"
                size="sm"
                label={t.a11y.deleteConversation}
                onClick={(e) => {
                  e.stopPropagation();
                  onDeleteThread(thread.conversationId);
                }}
                className="absolute right-2 w-8 px-0 opacity-0 hover:text-fg-danger group-hover:opacity-100 focus:opacity-100"
              >
                <TrashIcon size={15} />
              </IconButton>
            </div>
          );
        })}
      </div>
    </div>
  );
};
