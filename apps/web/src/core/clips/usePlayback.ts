"use client";

import * as React from "react";

const REDUCE = "(prefers-reduced-motion: reduce)";
const subscribe = (notify: () => void) => {
  const query = window.matchMedia(REDUCE);
  query.addEventListener("change", notify);
  return () => query.removeEventListener("change", notify);
};

/** True under reduced motion or Save-Data (and on the server): the poster, the finished state, stands in for a moving picture. */
export function useStillMotion(): boolean {
  const reduced = React.useSyncExternalStore(
    subscribe,
    () => window.matchMedia(REDUCE).matches,
    () => true,
  );
  const saveData = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection?.saveData;
  return reduced || Boolean(saveData);
}

/** Whether the element is on screen (at least `threshold` of it): what decides when a recording plays and when it pauses. */
export function useOnScreen(ref: React.RefObject<Element | null>, threshold = 0.4): boolean {
  const [onScreen, setOnScreen] = React.useState(false);
  React.useEffect(() => {
    const el = ref.current;
    if (!el || typeof IntersectionObserver === "undefined") {
      return;
    }
    const io = new IntersectionObserver(([entry]) => setOnScreen(entry.isIntersecting), { threshold });
    io.observe(el);
    return () => io.disconnect();
  }, [ref, threshold]);
  return onScreen;
}
