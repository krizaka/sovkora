import * as React from "react";

/** The JSON headers the integrations' BFF calls carry; the BFF adds the session's Bearer token server-side. */
export function useSettingsHeaders() {
  return React.useCallback(async (): Promise<Record<string, string>> => ({ "Content-Type": "application/json" }), []);
}
