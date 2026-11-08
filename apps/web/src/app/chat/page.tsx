"use client";

import React, { Suspense } from "react";
import { ChatWindow } from "@/features/chat-session/components/ChatWindow";
import { AppShell } from "@/components/layout/AppShell";
import { useAuth } from "@/core/hooks/useAuth";
import { useRouter, useSearchParams } from "next/navigation";

function ChatContent() {
  const searchParams = useSearchParams();
  const conversationId = searchParams.get("conversationId") || "";

  return <ChatWindow key={conversationId} initialConversationId={conversationId} />;
}

/**
 * ChatPage component providing the main chat interface within the workspace.
 *
 * @returns The rendered React element for the chat page.
 */
export default function ChatPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading } = useAuth();

  React.useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.push("/login");
    }
  }, [isLoading, isAuthenticated, router]);

  if (isLoading || !isAuthenticated) return null;

  return (
    <AppShell>
      <main className="flex-1 flex flex-col overflow-hidden bg-surface-0">
        <Suspense fallback={<output className="p-6 text-fg-muted text-sm animate-pulse">Loading chat...</output>}>
          <ChatContent />
        </Suspense>
      </main>
    </AppShell>
  );
}
