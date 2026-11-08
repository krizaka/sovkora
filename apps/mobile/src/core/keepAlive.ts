/**
 * @file keepAlive.ts
 * @description Keeps the connection to the edge warm while the app is open.
 *
 * iOS talks to the edge over HTTP/3 (QUIC). The edge's proxy drops a QUIC connection after ~30 s of silence and the phone
 * is not told: the next request (a chat POST, a tab's GET) is sent on the dead connection, waits ~6 s, then fails with
 * "The network connection was lost" — and the retry goes down the same dead connection. A tiny request every
 * KEEP_ALIVE_MS (well inside the idle limit) means a person never meets that connection. Only while the app is in the
 * foreground and signed in; nothing is read from the answer.
 */
import { useEffect } from "react";
import { AppState } from "react-native";
import { apiRequest } from "../config/api";

/** Well under the ~30 s the proxy keeps an idle QUIC connection. */
export const KEEP_ALIVE_MS = 15_000;

/** Pings the edge on an interval while `token` is set and the app is active. */
export function useKeepAlive(token: string | null): void {
  useEffect(() => {
    if (token === null) return;
    const ping = (): void => {
      if (AppState.currentState === "active") void apiRequest("/api/v1/profile", { token }).catch(() => undefined);
    };
    const timer = setInterval(ping, KEEP_ALIVE_MS);
    return () => clearInterval(timer);
  }, [token]);
}
