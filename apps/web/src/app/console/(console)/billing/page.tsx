"use client";

import { Tabs } from "@krizaka/ui/tabs";
import { PlansScreen } from "@/console/features/billing/components/PlansScreen";
import { PacksScreen } from "@/console/features/billing/components/PacksScreen";
import { ModelPricingScreen } from "@/console/features/billing/components/ModelPricingScreen";
import { PricebookScreen } from "@/console/features/billing/components/PricebookScreen";
import { KeyUsageScreen } from "@/console/features/billing/components/KeyUsageScreen";
import { PricesScreen } from "@/console/features/billing/components/PricesScreen";
import { UsageScreen } from "@/console/features/billing/components/UsageScreen";
import { WalletScreen } from "@/console/features/billing/components/WalletScreen";

const TABS = [
  { id: "usage", label: "Consommation" },
  { id: "keys", label: "Clés des produits" },
  { id: "models", label: "Tarifs par modèle" },
  { id: "pricebook", label: "Tarifs par capacité" },
  { id: "plans", label: "Offres" },
  { id: "prices", label: "Catalogue de prix" },
  { id: "packs", label: "Packs métier" },
  { id: "wallets", label: "Portefeuilles" },
] as const;

const TAB_IDS = TABS.map((entry) => entry.id);

/**
 * The billing console.
 *
 * Consumption opens first, not the catalogue: the refusal rate is the screen that
 * tells you whether the pricing is working, and an admin arriving here usually
 * wants to know that before changing anything.
 */
export default function BillingPage() {
  return (
    <main className="mx-auto max-w-7xl space-y-8 px-4 py-8 sm:px-6">
      <header>
        <h1 className="font-display text-3xl font-bold tracking-tight text-fg">Facturation &amp; crédits</h1>
        <p className="text-sm text-fg-muted">
          Tout ce qui est commercial vit en base et s’édite ici — jamais dans le code.
        </p>
      </header>

      <Tabs.Root routeKey="tab" routeValues={TAB_IDS}>
        <Tabs.List aria-label="Facturation" className="overflow-x-auto">
          {TABS.map((entry) => (
            <Tabs.Trigger key={entry.id} value={entry.id}>
              {entry.label}
            </Tabs.Trigger>
          ))}
        </Tabs.List>
        <Tabs.Content value="usage">
          <UsageScreen />
        </Tabs.Content>
        <Tabs.Content value="keys">
          <KeyUsageScreen />
        </Tabs.Content>
        <Tabs.Content value="models">
          <ModelPricingScreen />
        </Tabs.Content>
        <Tabs.Content value="pricebook">
          <PricebookScreen />
        </Tabs.Content>
        <Tabs.Content value="plans">
          <PlansScreen />
        </Tabs.Content>
        <Tabs.Content value="prices">
          <PricesScreen />
        </Tabs.Content>
        <Tabs.Content value="packs">
          <PacksScreen />
        </Tabs.Content>
        <Tabs.Content value="wallets">
          <WalletScreen />
        </Tabs.Content>
      </Tabs.Root>
    </main>
  );
}
