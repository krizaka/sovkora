import * as React from "react";
import { getSession } from "next-auth/react";

/** The authenticated JSON headers the integrations' BFF calls carry. */
export function useSettingsHeaders() {
  return React.useCallback(async (): Promise<Record<string, string>> => {
    const session = await getSession();
    return {
      "Content-Type": "application/json",
      ...(session?.user?.id ? { Authorization: `Bearer ${session.user.id}` } : {}),
    };
  }, []);
}
