"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Manages the chat scroll viewport: auto-scrolls to the latest message and exposes a
 * "scroll to bottom" FAB when the user has scrolled away from the bottom.
 *
 * @param deps - Reactive values that should re-trigger auto-scroll (messages, pending flags).
 * @returns Refs for the scroll container + end anchor, FAB visibility, and handlers.
 */
export function useScrollFab(deps: readonly unknown[]) {
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const scrollContainerRef = useRef<HTMLDivElement | null>(null);
  const [showScrollFab, setShowScrollFab] = useState(false);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  const handleScroll = useCallback(() => {
    const el = scrollContainerRef.current;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    setShowScrollFab(distanceFromBottom > 200);
  }, []);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    setShowScrollFab(false);
  }, []);

  return {
    messagesEndRef,
    scrollContainerRef,
    showScrollFab,
    handleScroll,
    scrollToBottom,
  };
}
