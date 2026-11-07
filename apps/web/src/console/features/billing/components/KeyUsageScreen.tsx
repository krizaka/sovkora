"use client";

import { useQuery } from "@krizaka/query";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { formatCredits } from "@krizaka/orazaka-shared";
import { BillingAdminApi } from "@/console/services/billing.admin.api";
import { OrganizationsAdminApi, type Organization } from "@/console/services/organizations.admin.api";
import { groupByAccount, type AccountUsage } from "@/console/features/billing/utils/key-usage";

const DAYS = 30;
const CONSUMERS_LIMIT = 200;

const COLUMNS: DataTableColumn<AccountUsage>[] = [
  { id: "account", header: "Compte produit", sortable: true, card: "title" },
  {
    id: "keys",
    header: "Clés",
    sortable: true,
    align: "end",
    value: (row) => row.keys.length,
    cell: (row) => row.keys.map((k) => k.keyPrefix).join(", "),
  },
  { id: "events", header: "Requêtes", sortable: true, align: "end" },
  {
    id: "creditsCharged",
    header: "Crédits",
    sortable: true,
    align: "end",
    cell: (row) => formatCredits(row.creditsCharged),
  },
];

/** One organization: the usage and cost of each product's key, side by side. */
function OrganizationUsage({ organization }: Readonly<{ organization: Organization }>) {
  const keys = useQuery({
    queryKey: ["organization-service-keys", organization.id],
    queryFn: () => OrganizationsAdminApi.serviceKeys(organization.id),
  });
  const consumers = useQuery({
    queryKey: ["billing-top-consumers", DAYS, CONSUMERS_LIMIT],
    queryFn: () => BillingAdminApi.topConsumers(DAYS, CONSUMERS_LIMIT),
  });
  const failed = keys.error ?? consumers.error;
  const rows = keys.data && consumers.data ? groupByAccount(keys.data, consumers.data) : null;

  return (
    <DataTable
      caption={`Clés de ${organization.name}`}
      rows={rows ?? []}
      columns={COLUMNS}
      getRowId={(row) => row.ownerUserId}
      defaultSort={{ column: "creditsCharged", direction: "descending" }}
      loading={rows === null && !failed}
      loadingRows={3}
      error={
        failed ? (
          <p className="text-sm text-fg-danger">{failed instanceof Error ? failed.message : "Chargement impossible"}</p>
        ) : undefined
      }
      labels={{ loading: "Lecture des clés", empty: "Aucune clé de service rattachée à cette organisation." }}
    />
  );
}

/**
 * The cost of each product's key, grouped by the account that owns it (orazaka#372). The keys come from the identity
 * service (the organization owns its `svc_*` accounts), the cost from the billing ledger; the join is the owner's id.
 */
export function KeyUsageScreen() {
  const organizations = useQuery({ queryKey: ["organizations"], queryFn: OrganizationsAdminApi.list });
  return (
    <section className="space-y-6">
      <header>
        <h2 className="text-lg font-semibold text-fg">Clés des produits</h2>
        <p className="text-sm text-fg-muted">
          {DAYS} derniers jours. Chaque produit garde sa clé et son offre : ici, ce que chacune consomme.
        </p>
      </header>
      {organizations.error ? <p className="text-sm text-fg-danger">Organisations illisibles.</p> : null}
      {(organizations.data ?? []).map((organization) => (
        <div key={organization.id} className="space-y-2">
          <h3 className="text-base font-medium text-fg">{organization.name}</h3>
          <OrganizationUsage organization={organization} />
        </div>
      ))}
      {organizations.data?.length === 0 ? <p className="text-sm text-fg-muted">Aucune organisation.</p> : null}
    </section>
  );
}
