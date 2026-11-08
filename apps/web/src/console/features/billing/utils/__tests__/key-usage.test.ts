import { groupByAccount } from "@/console/features/billing/utils/key-usage";

const key = (id: string, ownerUserId: string, ownerUsername: string) => ({
  id,
  name: id,
  keyPrefix: `oz_${id}`,
  ownerUserId,
  ownerUsername,
});

describe("groupByAccount (orazaka#372)", () => {
  it("groups the keys of one product account and reads its usage by owner id", () => {
    const rows = groupByAccount(
      [
        key("k1", "u-yomizaka", "svc_yomizaka"),
        key("k2", "u-yomizaka", "svc_yomizaka"),
        key("k3", "u-akinaza", "svc_akinaza"),
      ],
      [
        { actorId: "u-yomizaka", events: 12, creditsCharged: 340 },
        { actorId: "u-akinaza", events: 3, creditsCharged: 40 },
      ],
    );
    expect(rows.map((r) => [r.account, r.keys.length, r.events, r.creditsCharged])).toEqual([
      ["svc_yomizaka", 2, 12, 340],
      ["svc_akinaza", 1, 3, 40],
    ]);
  });

  it("shows an account the ledger does not know as zero, and ignores consumers outside the organization", () => {
    const rows = groupByAccount(
      [key("k1", "u-akinaza", "svc_akinaza")],
      [{ actorId: "someone-else", events: 99, creditsCharged: 999 }],
    );
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ account: "svc_akinaza", events: 0, creditsCharged: 0 });
  });

  it("breaks a tie by account name, so the order is stable", () => {
    const rows = groupByAccount([key("a", "u2", "svc_b"), key("b", "u1", "svc_a")], []);
    expect(rows.map((r) => r.account)).toEqual(["svc_a", "svc_b"]);
  });
});
