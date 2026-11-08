/** @jest-environment node */
import { findTypedPrices } from "@krizaka/settings";
import { translations } from "@/core/context/translations";

/**
 * Sovkora#417: every amount comes from the price catalogue (billing, edited by the admin), never typed in a message.
 * `findTypedPrices` is the one rule of the family (@krizaka/settings); this test runs it over each locale's messages.
 */
describe("no amount is typed in a message", () => {
  it.each(Object.keys(translations) as (keyof typeof translations)[])("%s", (locale) => {
    expect(findTypedPrices(translations[locale])).toEqual([]);
  });

  it("the rule itself finds an amount in a French and an English writing", () => {
    expect(findTypedPrices({ a: { b: "45 $ par mois" }, c: ["ok", "120 CAD"], d: "$10" })).toEqual(["a.b", "c.1", "d"]);
  });
});
