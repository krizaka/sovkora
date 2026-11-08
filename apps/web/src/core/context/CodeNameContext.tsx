"use client";

import { createContext, type ReactNode, useContext } from "react";
import { CODE_NAME } from "@/core/brand/brand";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";

const DevDeployment = createContext(false);

/** Says whether this deployment is a development one (the server decides: `!indexable()`, the same signal as the notice). */
export function CodeNameProvider({ dev, children }: Readonly<{ dev: boolean; children: ReactNode }>) {
  return <DevDeployment.Provider value={dev}>{children}</DevDeployment.Provider>;
}

/**
 * « code name: orazaka » / « nom de code : orazaka »: the line under « Sovkora » on a development deployment, so the host
 * (www.orazaka.com) and the screen can be followed. `null` in production. The words are the `landing.stage.codeName` messages.
 */
export function useCodeName(): string | null {
  const dev = useContext(DevDeployment);
  const { t } = useTranslation();
  return dev ? fill(t.landing.stage.codeName, { code: CODE_NAME }) : null;
}
