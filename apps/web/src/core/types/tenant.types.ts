/**
 * Configuration interface representing the current tenant layout and theme settings.
 */
export interface TenantConfig {
  accentClass: string; // Maps the selected accent color key to class string (e.g. 'rose' | 'emerald' | 'amber' | 'zinc')
  displayName: string; // App/brand name
  tagline: string; // App brand tagline
  tenantId: string; // Abstract tenant UUID/string
  layoutMode: "standard" | "compact";
}
