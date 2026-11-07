import React, { useState } from "react";
import { EditIcon, MenuIcon } from "@krizaka/icons";
import { IconButton } from "@krizaka/ui/button";

interface ChatHeaderProps {
  threadTitle: string;
  onOpenDrawer: () => void;
  onRename: (title: string) => void;
  t: {
    chat: {
      sessionTitle: string;
    };
    a11y: {
      renameConversation: string;
      openConversations: string;
    };
  };
}

/**
 * Chat session header — the thread title, inline-editable. The conversation's id is an address for
 * the application, not something its reader needs, so it is not shown.
 * Calm Obsidian 2026 — solid surface, no backdrop-blur.
 */
export function ChatHeader({ threadTitle, onOpenDrawer, onRename, t }: Readonly<ChatHeaderProps>) {
  const [isEditing, setIsEditing] = useState(false);
  // What the person typed, or nothing: the title shown is the thread's own until they edit it.
  const [draft, setDraft] = useState<string | null>(null);
  const editTitle = draft ?? threadTitle;
  const setEditTitle = (value: string) => setDraft(value === threadTitle ? null : value);

  const handleSave = () => {
    setIsEditing(false);
    if (editTitle.trim() && editTitle !== threadTitle) {
      onRename(editTitle.trim());
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      handleSave();
    } else if (e.key === "Escape") {
      setEditTitle(threadTitle);
      setIsEditing(false);
    }
  };

  return (
    <header className="p-4 border-b border-border-subtle bg-surface-1 flex items-center justify-between">
      <div className="flex items-center gap-3 w-full">
        <IconButton variant="ghost" onClick={onOpenDrawer} label={t.a11y.openConversations} className="md:hidden">
          <MenuIcon size={20} />
        </IconButton>
        <section className="flex flex-col flex-1 min-w-0">
          <section className="flex items-center gap-2">
            {isEditing ? (
              <input
                type="text"
                id="rename-session-input"
                value={editTitle}
                onChange={(e) => setEditTitle(e.target.value)}
                onBlur={handleSave}
                onKeyDown={handleKeyDown}
                autoFocus
                className="text-sm font-semibold text-fg bg-surface-2 border border-border-default rounded px-2 py-0.5 focus:outline-none focus:ring-1 focus:ring-ring max-w-md w-full"
              />
            ) : (
              <>
                <h1 className="text-sm font-semibold text-fg truncate">{threadTitle || t.chat.sessionTitle}</h1>
                <IconButton
                  id="btn-edit-session-title"
                  variant="ghost"
                  size="sm"
                  className="w-8 px-0"
                  onClick={() => setIsEditing(true)}
                  label={t.a11y.renameConversation}
                >
                  <EditIcon size={15} />
                </IconButton>
              </>
            )}
          </section>
        </section>
      </div>
    </header>
  );
}
