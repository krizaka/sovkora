"use client";

import * as React from "react";
import { useQuery, useQueryClient } from "@krizaka/query";

export const WEBHOOK_EVENTS = ["run.completed", "run.failed", "run.cancelled"] as const;
export type WebhookEvent = (typeof WEBHOOK_EVENTS)[number];

/** The key of an event's words in the dictionary (a dictionary key cannot hold a dot). */
export const EVENT_WORD = {
  "run.completed": "completed",
  "run.failed": "failed",
  "run.cancelled": "cancelled",
} as const;

/** An endpoint as the API lists it — never carries the signing secret. */
export interface WebhookEndpoint {
  id: string;
  url: string;
  events: WebhookEvent[];
  enabled: boolean;
  createdAt: string;
}

/** One line of an endpoint's delivery log. */
export interface WebhookDelivery {
  id: string;
  runId: string;
  event: WebhookEvent;
  status: "PENDING" | "DELIVERED" | "DEAD";
  attempts: number;
  lastHttpStatus: number | null;
  lastError: string | null;
  createdAt: string;
  deliveredAt: string | null;
}

/** The one-time reveal of a signing secret (on create and on rotate). */
export interface RevealedSecret {
  url: string;
  secret: string;
}

export type WebhookError = "none" | "url" | "limit" | "generic";

const BASE = "/api/v1/studios/webhooks";
const WEBHOOKS_KEY = ["webhooks"] as const;

/** An https address with a host and no credentials — what the API accepts. */
export function isHttpsUrl(value: string): boolean {
  try {
    const u = new URL(value.trim());
    return u.protocol === "https:" && u.hostname.length > 0 && u.username === "" && u.password === "";
  } catch {
    return false;
  }
}

/** The error to show for a failed create, from the API's status. */
export function errorForStatus(status: number): WebhookError {
  if (status === 400) return "url";
  if (status === 409) return "limit";
  return "generic";
}

/**
 * Owns the webhook endpoints of the signed-in user: list, create, rotate, delete, and the delivery log of one endpoint.
 * The signing secret comes back exactly once (create, rotate) and is surfaced through `revealed`, never refetched.
 *
 * @param fetchHeaders - Async factory for authenticated request headers.
 */
export function useWebhooks(fetchHeaders: () => Promise<Record<string, string>>) {
  const [isSaving, setIsSaving] = React.useState(false);
  const [error, setError] = React.useState<WebhookError>("none");
  const [revealed, setRevealed] = React.useState<RevealedSecret | null>(null);
  const [log, setLog] = React.useState<{ endpoint: WebhookEndpoint; deliveries: WebhookDelivery[] } | null>(null);

  const queryClient = useQueryClient();
  // The list is optional: a failed call leaves it empty and the empty state explains what to do.
  const list = useQuery({
    queryKey: WEBHOOKS_KEY,
    queryFn: async () => {
      const res = await fetch(BASE, { headers: await fetchHeaders() });
      if (!res.ok) throw new Error(`webhooks ${res.status}`);
      return (await res.json()) as WebhookEndpoint[];
    },
  });
  const endpoints = list.data ?? [];
  const isLoading = list.isPending;
  const load = () => queryClient.invalidateQueries({ queryKey: WEBHOOKS_KEY });

  const create = async (url: string, events: WebhookEvent[]): Promise<boolean> => {
    setError("none");
    if (!isHttpsUrl(url)) {
      setError("url");
      return false;
    }
    setIsSaving(true);
    try {
      const res = await fetch(BASE, {
        method: "POST",
        headers: await fetchHeaders(),
        body: JSON.stringify({ url: url.trim(), events }),
      });
      if (!res.ok) {
        setError(errorForStatus(res.status));
        return false;
      }
      const created = (await res.json()) as WebhookEndpoint & { secret: string };
      setRevealed({ url: created.url, secret: created.secret });
      await load();
      return true;
    } catch {
      setError("generic");
      return false;
    } finally {
      setIsSaving(false);
    }
  };

  const rotate = async (endpoint: WebhookEndpoint) => {
    setIsSaving(true);
    try {
      const res = await fetch(`${BASE}/${endpoint.id}/rotate`, { method: "POST", headers: await fetchHeaders() });
      if (res.ok) setRevealed({ url: endpoint.url, secret: ((await res.json()) as { secret: string }).secret });
    } catch {
      // Nothing changed server-side if the call did not complete.
    } finally {
      setIsSaving(false);
    }
  };

  const remove = async (endpoint: WebhookEndpoint) => {
    queryClient.setQueryData<WebhookEndpoint[]>(WEBHOOKS_KEY, (prev) =>
      (prev ?? []).filter((e) => e.id !== endpoint.id),
    );
    try {
      await fetch(`${BASE}/${endpoint.id}`, { method: "DELETE", headers: await fetchHeaders() });
    } catch {
      await load();
    }
  };

  const openLog = async (endpoint: WebhookEndpoint) => {
    setLog({ endpoint, deliveries: [] });
    try {
      const res = await fetch(`${BASE}/${endpoint.id}/deliveries`, { headers: await fetchHeaders() });
      if (res.ok) setLog({ endpoint, deliveries: (await res.json()) as WebhookDelivery[] });
    } catch {
      // The log stays empty.
    }
  };

  return {
    endpoints,
    isLoading,
    isSaving,
    error,
    revealed,
    log,
    create,
    rotate,
    remove,
    openLog,
    closeLog: () => setLog(null),
    dismissRevealed: () => setRevealed(null),
    clearError: () => setError("none"),
  };
}
