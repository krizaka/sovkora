"use client";

import { useEffect, useState } from "react";

/**
 * The current time, refreshed every `intervalMs` while `active` — what an ETA counts down from. No timer runs when
 * nothing is waiting.
 */
export function useNow(active: boolean, intervalMs = 1000): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => setNow(new Date()), intervalMs);
    return () => clearInterval(timer);
  }, [active, intervalMs]);
  return now;
}
