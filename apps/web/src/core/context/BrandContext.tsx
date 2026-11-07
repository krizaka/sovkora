"use client";

import { createContext, useContext, type ReactNode } from "react";
import { DEFAULT_BRAND } from "@/core/brand/brand";

const BrandContext = createContext<string>(DEFAULT_BRAND);

/** Hands the name read on the server (`brand.displayName`) to the client tree. */
export function BrandProvider({ name, children }: Readonly<{ name: string; children: ReactNode }>) {
  return <BrandContext.Provider value={name}>{children}</BrandContext.Provider>;
}

/** The product name to show. */
export function useBrand(): string {
  return useContext(BrandContext);
}
