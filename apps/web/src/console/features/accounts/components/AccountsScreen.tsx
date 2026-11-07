"use client";

import * as React from "react";
import { Alert } from "@krizaka/ui/alert";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Field, Input } from "@krizaka/ui/field";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { consoleCopy } from "@/console/core/copy/console";
import { PlatformAdminApi } from "@/console/services/platform.admin.api";
import type { AdminUser } from "@/console/services/platform.schemas";
import { useAccounts, type Accounts } from "@/console/features/accounts/hooks/useAccounts";
import { erasureArmed, stateActions } from "@/console/features/accounts/utils/accounts";

const a = consoleCopy.accounts;

/** What an administrator does to one account: its state, its role, the Law 25 export, and its erasure (typed e-mail). */
function AccountActions({ user, accounts }: Readonly<{ user: AdminUser; accounts: Accounts }>) {
  const [typed, setTyped] = React.useState("");
  const next = stateActions(user);
  const inputId = `erase-${user.id}`;
  return (
    <section className="flex flex-col gap-4 p-3" aria-label={user.email}>
      <div className="flex flex-wrap gap-2">
        <Button
          variant="secondary"
          size="sm"
          onClick={() =>
            void accounts.run(() =>
              next.status === "suspend" ? PlatformAdminApi.suspendUser(user.id) : PlatformAdminApi.restoreUser(user.id),
            )
          }
        >
          {next.status === "suspend" ? a.suspend : a.restore}
        </Button>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => void accounts.run(() => PlatformAdminApi.setAdmin(user.id, next.role === "grant"))}
        >
          {next.role === "grant" ? a.grant : a.withdraw}
        </Button>
        <Button variant="secondary" size="sm" asChild>
          <a
            href={`/api/v1/admin/users/${encodeURIComponent(user.id)}/export`}
            download={`orazaka-account-${user.id}.json`}
          >
            {a.exportData}
          </a>
        </Button>
      </div>
      <div className="flex max-w-xl flex-col gap-2 border-t border-border-subtle pt-3">
        <h3 className="text-sm font-bold text-fg">{a.eraseTitle}</h3>
        <p className="text-sm text-fg-secondary">{a.eraseLead}</p>
        <Field.Root>
          <Field.Label htmlFor={inputId}>{a.eraseConfirm}</Field.Label>
          <Input
            id={inputId}
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            autoComplete="off"
            spellCheck={false}
          />
        </Field.Root>
        <div>
          <Button
            variant="danger"
            size="sm"
            disabled={!erasureArmed(typed, user.email)}
            onClick={() => void accounts.run(() => PlatformAdminApi.eraseUser(user.id, typed))}
          >
            {a.eraseSubmit}
          </Button>
        </div>
      </div>
    </section>
  );
}

const emailCell = (u: AdminUser) => <span className="font-semibold text-fg">{u.email}</span>;

const roleCell = (u: AdminUser) => (
  <Badge tone={u.admin ? "accent" : "neutral"}>{u.admin ? a.administrator : a.member}</Badge>
);

const stateCell = (u: AdminUser) => (
  <Badge tone={u.enabled ? "success" : "danger"}>{u.enabled ? a.active : a.suspended}</Badge>
);

/** The accounts of the platform with a search box; each row opens its actions. */
export function AccountsScreen() {
  const accounts = useAccounts();
  const columns = React.useMemo<DataTableColumn<AdminUser>[]>(
    () => [
      {
        id: "email",
        header: a.email,
        sortable: true,
        card: "title",
        cell: emailCell,
      },
      { id: "username", header: a.user, sortable: true },
      {
        id: "admin",
        header: a.role,
        value: (u) => (u.admin ? "admin" : "member"),
        cell: roleCell,
      },
      {
        id: "enabled",
        header: a.state,
        value: (u) => (u.enabled ? "active" : "suspended"),
        cell: stateCell,
      },
      { id: "provider", header: a.provider, value: (u) => u.provider ?? "local" },
      {
        id: "createdAt",
        header: a.created,
        sortable: true,
        value: (u) => u.createdAt ?? "",
        cell: (u) => (u.createdAt ? u.createdAt.slice(0, 10) : "—"),
      },
    ],
    [],
  );
  return (
    <article className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-3xl font-bold tracking-tight text-fg">{a.title}</h1>
        <p className="max-w-3xl text-sm text-fg-secondary">{a.lead}</p>
      </header>
      {accounts.hasError && !accounts.isLoading && (
        <Alert
          tone="warning"
          action={
            <Button variant="secondary" size="sm" onClick={accounts.reload}>
              {a.retry}
            </Button>
          }
        >
          {a.error}
        </Alert>
      )}
      {accounts.problem && <Alert tone="danger">{accounts.problem}</Alert>}
      <Field.Root className="max-w-md">
        <Field.Label htmlFor="account-search">{a.search}</Field.Label>
        <Input
          id="account-search"
          value={accounts.query}
          onChange={(e) => accounts.setQuery(e.target.value)}
          autoComplete="off"
        />
      </Field.Root>
      <DataTable
        caption={a.title}
        rows={accounts.users}
        columns={columns}
        getRowId={(u) => u.id}
        rowLabel={(u) => u.email}
        loading={accounts.isLoading}
        defaultPageSize={25}
        renderDetail={(u) => <AccountActions user={u} accounts={accounts} />}
        footer={
          <p className="px-3 py-2 text-xs text-fg-muted">{a.results.replace("{count}", String(accounts.total))}</p>
        }
      />
    </article>
  );
}
