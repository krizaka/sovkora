"use client";

import { useCallback, useEffect, useState } from "react";
import { Badge, Button } from "@krizaka/orazaka-design-system";
import { Card } from "@krizaka/ui/card";
import { formatCredits, type Pack } from "@krizaka/orazaka-shared";
import { money } from "@/console/features/billing/utils/money";
import { PackEditor } from "./PackEditor";
import { BillingAdminApi } from "@/console/services/billing.admin.api";

/**
 * The packs screen — the **price and the grant** an admin sets for each bundle métier.
 *
 * It used to group the list by category, mirroring how a buyer browses. It no longer can,
 * and that is a real narrowing rather than an oversight: the category moved to the
 * catalogue in the studio context with ADR-036, and this console has no editor for it
 * until phase 4. Listing flat is honest — a grouping this screen could not edit would
 * imply an authority it does not have.
 *
 * The list is keyed on `packKey` because that is now the only identity billing holds. The
 * matching `pack` row in the catalogue carries the name a buyer sees; a price with no
 * catalogue row is a pack nobody can browse, which `PackCoherenceRules` fails the build on.
 *
 * Withdrawing does not revoke: actors who bought the pack keep it. The button says
 * "Retirer de la vente" for that reason — "Supprimer" would describe an action this
 * console cannot perform and should not imply.
 */
export function PacksScreen() {
  const [packs, setPacks] = useState<Pack[]>([]);
  const [editing, setEditing] = useState<Pack | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setPacks(await BillingAdminApi.listPacks(true));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Chargement impossible");
    }
  }, []);

  useEffect(() => {
    // Deferred rather than called inline: loading synchronously inside the effect
    // sets state during the same commit and cascades a second render.
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  const save = async () => {
    if (!editing) return;
    try {
      await BillingAdminApi.savePack(editing);
      setEditing(null);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Enregistrement impossible");
    }
  };

  const withdraw = async (packKey: string) => {
    try {
      await BillingAdminApi.withdrawPack(packKey);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Retrait impossible");
    }
  };

  const blankPack = (): Pack => ({
    packKey: "",
    priceCents: 0,
    includedCredits: 0,
    isActive: true,
    entitlements: [],
  });

  return (
    <section className="space-y-6">
      <header className="flex items-start justify-between">
        <div>
          <h2 className="text-lg font-semibold text-fg">Packs métier</h2>
          <p className="text-sm text-fg-muted">
            Le prix et les droits d’un bundle acheté en plus d’une offre. Son nom, sa catégorie et son icône vivent dans
            le catalogue (contexte Studio). Un pack retiré reste acquis à ceux qui l’ont acheté.
          </p>
        </div>
        <Button onClick={() => setEditing(blankPack())}>Nouveau pack</Button>
      </header>

      {error && <p className="text-sm text-fg-danger">{error}</p>}

      {packs.length === 0 ? (
        <p className="text-xs text-fg-muted">Aucun pack tarifé.</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {packs.map((pack) => (
            <PackTile
              key={pack.packKey}
              pack={pack}
              onEdit={setEditing}
              onWithdraw={(packKey) => void withdraw(packKey)}
            />
          ))}
        </div>
      )}

      {editing && (
        <PackEditor pack={editing} onChange={setEditing} onSave={() => void save()} onCancel={() => setEditing(null)} />
      )}
    </section>
  );
}

function PackTile({
  pack,
  onEdit,
  onWithdraw,
}: Readonly<{
  pack: Pack;
  onEdit: (pack: Pack) => void;
  onWithdraw: (packKey: string) => void;
}>) {
  return (
    <Card.Root className="p-5">
      <article className="space-y-3">
        <header className="flex items-start justify-between">
          <p className="font-medium text-fg">{pack.packKey}</p>
          {!pack.isActive && <Badge variant="warning">retiré</Badge>}
        </header>
        <p className="text-sm text-fg">
          {money(pack.priceCents)} · {formatCredits(pack.includedCredits)} crédits inclus
        </p>
        <p className="text-xs text-fg-muted">{pack.entitlements.length} droits</p>
        <div className="flex gap-2">
          <Button size="sm" variant="ghost" onClick={() => onEdit(pack)}>
            Modifier
          </Button>
          {pack.isActive && (
            <Button size="sm" variant="ghost" onClick={() => onWithdraw(pack.packKey)}>
              Retirer de la vente
            </Button>
          )}
        </div>
      </article>
    </Card.Root>
  );
}
