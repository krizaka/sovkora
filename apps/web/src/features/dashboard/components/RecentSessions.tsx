import * as React from "react";
import { Button } from "@krizaka/orazaka-design-system";
import { Icon } from "@krizaka/orazaka-design-system";
import { Card } from "@krizaka/ui/card";

interface Thread {
  conversationId: string;
  title: string;
  updatedAt: number;
}

interface RecentSessionsProps {
  recentThreads: Thread[];
  onStartNewChat: () => void;
  onResumeSession: (conversationId: string) => void;
  formatDate: (timestamp: number) => string;
  t: {
    dashboard: {
      recentActivity: string;
      noRecentSessions: string;
      startNewChat: string;
      resumeSession: string;
    };
  };
}

/**
 * Renders the recent chat sessions on the user dashboard — Calm Obsidian 2028.
 * Clean card list with design-token colors, hover-lift interaction, and
 * skeleton-ready empty state.
 *
 * @param props - The component properties.
 * @param props.recentThreads - List of thread metadata representing recent chat history.
 * @param props.onStartNewChat - Callback triggered to initiate a new conversation.
 * @param props.onResumeSession - Callback triggered to load an existing conversation thread.
 * @param props.formatDate - Utility function to format timestamp values into human-readable strings.
 * @param props.t - The localized translations dictionary.
 * @returns React element representing the recent sessions list or empty state.
 */
export function RecentSessions({
  recentThreads,
  onStartNewChat,
  onResumeSession,
  formatDate,
  t,
}: Readonly<RecentSessionsProps>) {
  return (
    <div className="md:col-span-3 space-y-4">
      <h3 className="hud-label px-1">{t.dashboard.recentActivity}</h3>

      <Card.Root className="p-(--orazaka-space-card) overflow-hidden">
        {recentThreads.length === 0 ? (
          <div className="text-center py-12">
            <div className="mx-auto mb-4 w-12 h-12 rounded-lg bg-surface-2 flex items-center justify-center">
              <Icon name="chat" size={20} className="text-fg-muted" />
            </div>
            <p className="fluid-sm text-fg-secondary mb-4">{t.dashboard.noRecentSessions}</p>
            <Button onClick={onStartNewChat} size="sm">
              {t.dashboard.startNewChat}
            </Button>
          </div>
        ) : (
          <ul className="divide-y divide-border-subtle -mx-1">
            {recentThreads.map((thread) => (
              <li key={thread.conversationId}>
                <Button
                  variant="ghost"
                  onClick={() => onResumeSession(thread.conversationId)}
                  className="group h-auto w-full justify-between px-3 py-3.5 text-left font-normal"
                >
                  <div className="flex items-center min-w-0 mr-4">
                    <div className="w-9 h-9 rounded-md bg-surface-2 flex items-center justify-center mr-3.5 text-fg-muted group-hover:text-accent transition-colors duration-200 border border-border-subtle">
                      <Icon name="chat" size={16} />
                    </div>
                    <div className="min-w-0">
                      <p className="fluid-sm font-semibold text-fg truncate max-w-[200px] sm:max-w-[320px]">
                        {thread.title}
                      </p>
                      <span className="fluid-xs text-fg-muted font-mono flex items-center mt-0.5">
                        <Icon name="history" size={12} className="mr-1" />
                        {formatDate(thread.updatedAt)}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center fluid-xs text-fg-muted font-medium group-hover:text-accent transition-colors duration-200">
                    <span className="mr-1.5 hidden sm:inline-block font-semibold ">{t.dashboard.resumeSession}</span>
                    <Icon name="arrowRight" size={14} className="transform transition-transform duration-200" />
                  </div>
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card.Root>
    </div>
  );
}
