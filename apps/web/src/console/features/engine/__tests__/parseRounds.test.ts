import { parseRounds } from "@/console/features/engine/utils/featureTools";
import { featureToolsCopy } from "@/console/core/copy/featureTools";

const en = featureToolsCopy.en;
const fr = featureToolsCopy.fr;
const RANGE = { min: 1, max: 32 };

describe("parseRounds", () => {
  it("accepts a whole number inside the server's range and refuses the rest with the range in words", () => {
    expect(parseRounds("5", RANGE, en)).toEqual({ ok: true, value: 5 });
    expect(parseRounds(" 32 ", RANGE, en)).toEqual({ ok: true, value: 32 });
    for (const bad of ["0", "33", "1.5", "-2", "", "abc"]) {
      expect(parseRounds(bad, RANGE, en).ok).toBe(false);
    }
    expect(parseRounds("0", RANGE, fr)).toEqual({
      ok: false,
      error: "Saisissez un nombre entier de 1 à 32.",
    });
  });
});
