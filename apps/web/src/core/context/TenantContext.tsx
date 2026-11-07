"use client";

/**
 * @file TenantContext.tsx
 * @description Context provider and hook for managing and resolving dynamic tenant configuration and styling accents.
 *
 * Client Directive: "use client"
 * State & Memoization: Uses React.useMemo to compute and cache layout config based on theme properties.
 * See: {@link TenantConfig}
 */

import * as React from "react";
import { useSettings } from "@/core/hooks/useSettings";
import { TenantConfig } from "@/core/types/tenant.types";
import { toAppearance, useAppearance } from "@/core/hooks/useAppearance";

/**
 * Interface mapping available visual utility classes for Tailwind css components.
 */
export interface AccentClasses {
  text: string;
  bg: string;
  hoverBg: string;
  border: string;
  ring: string;
  bgSoft: string;
  textBright: string;
  accentGradient: string;
  /** Text and icons laid on `bg` / `accentGradient`. */
  textOn: string;
}

/**
 * Map containing functional CSS classes corresponding to color theme accents.
 * Prevents hardcoding of color utility lists inside core components.
 */
export const accentMap: Record<string, AccentClasses> = {
  rose: {
    text: "text-danger",
    bg: "bg-danger",
    hoverBg: "hover:bg-danger",
    border: "border-danger",
    ring: "focus:ring-danger focus-visible:ring-danger",
    bgSoft: "bg-danger/20",
    textBright: "text-danger",
    accentGradient: "from-danger to-accent",
    textOn: "text-on-accent",
  },
  emerald: {
    text: "text-success",
    bg: "bg-success",
    hoverBg: "hover:bg-success",
    border: "border-success",
    ring: "focus:ring-success focus-visible:ring-success",
    bgSoft: "bg-success/20",
    textBright: "text-success",
    accentGradient: "from-success to-success",
    textOn: "text-on-accent",
  },
  amber: {
    text: "text-warning",
    bg: "bg-warning",
    hoverBg: "hover:bg-warning",
    border: "border-warning",
    ring: "focus:ring-warning focus-visible:ring-warning",
    bgSoft: "bg-warning/20",
    textBright: "text-warning",
    accentGradient: "from-warning to-warning",
    textOn: "text-on-accent",
  },
  zinc: {
    text: "text-fg-secondary",
    bg: "bg-surface-3",
    hoverBg: "hover:bg-surface-3",
    border: "border-border-subtle",
    ring: "focus:ring-border-subtle focus-visible:ring-border-subtle",
    bgSoft: "bg-surface-0/20",
    textBright: "text-fg-secondary",
    accentGradient: "from-surface-3 to-surface-3",
    textOn: "text-fg",
  },
};

/**
 * Shape of the Tenant context state object.
 */
interface TenantContextType {
  config: TenantConfig;
  accentClasses: AccentClasses;
}

/**
 * Context container for holding active tenant metadata.
 */
const TenantContext = React.createContext<TenantContextType | undefined>(undefined);

/**
 * TenantProvider component wrapping context injection.
 * Resolves active tenant config from global state hooks and passes down theme layouts.
 *
 * @param props - Component React properties.
 * @param props.children - Child nodes to be injected inside the provider wrapper.
 * @returns The context Provider rendering element.
 */
export function TenantProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const { settings } = useSettings();

  const themeAccent = settings?.themeAccent || "zinc";
  const themeName = settings?.themeName || "Sovkora";
  const themeTagline = settings?.themeTagline || "Decoupled Intelligence";
  const themeLayout = settings?.themeLayout || "standard";
  // Check user settings preferences or fallback to a default ID
  const tenantId = settings?.tenantId || "orazaka-default";

  const { setAppearance } = useAppearance();

  React.useEffect(() => {
    if (settings?.theme) {
      setAppearance(toAppearance(settings.theme));
    }
  }, [settings?.theme, setAppearance]);

  const contextValue = React.useMemo<TenantContextType>(() => {
    const normalizedAccent = (() => {
      if (themeAccent.includes("rose")) return "rose";
      if (themeAccent.includes("emerald")) return "emerald";
      if (themeAccent.includes("amber")) return "amber";
      return "zinc";
    })();
    const accentClass = accentMap[normalizedAccent] ? normalizedAccent : "zinc";
    const accentClasses = accentMap[accentClass];

    return {
      config: {
        accentClass,
        displayName: themeName,
        tagline: themeTagline,
        tenantId,
        layoutMode: themeLayout,
      },
      accentClasses,
    };
  }, [themeAccent, themeName, themeTagline, themeLayout, tenantId]);

  return <TenantContext.Provider value={contextValue}>{children}</TenantContext.Provider>;
}

/**
 * Custom React Hook to consume the Tenant context.
 *
 * @throws {Error} If called outside of a wrapping {@link TenantProvider}.
 * @returns The active {@link TenantContextType} state configuration.
 */
export function useTenant() {
  const context = React.useContext(TenantContext);
  if (context === undefined) {
    throw new Error("useTenant must be used within a TenantProvider");
  }
  return context;
}
