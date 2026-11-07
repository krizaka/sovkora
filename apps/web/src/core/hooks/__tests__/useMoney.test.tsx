import { renderHook } from "@testing-library/react";
import { BILLING_CURRENCY, MONEY_LOCALES } from "@/core/constants/money.constants";
import { useMoney } from "@/core/hooks/useMoney";

let mockLocale: "en" | "fr" = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: {}, locale: mockLocale }),
}));

// Intl separates "29,00" from "$CA" with a no-break space; compare on plain spaces.
const plain = (text: string) => text.replace(/[  ]/g, " ");

describe("useMoney — Canadian dollars by default, the currency always named", () => {
  it("bills in CAD unless the deployment says otherwise", () => {
    expect(BILLING_CURRENCY).toBe("CAD");
    expect(MONEY_LOCALES).toEqual({ en: "en", fr: "fr" });
  });

  it("writes CA$ in English", () => {
    mockLocale = "en";
    const { result } = renderHook(() => useMoney());
    expect(result.current(2900)).toBe("CA$29.00");
    expect(result.current(0)).toBe("CA$0.00");
    expect(result.current(123456)).toBe("CA$1,234.56");
  });

  it("writes $CA in French", () => {
    mockLocale = "fr";
    const { result } = renderHook(() => useMoney());
    expect(plain(result.current(2900))).toBe("29,00 $CA");
    expect(plain(result.current(123456))).toBe("1 234,56 $CA");
  });

  it("lets a plan's own currency win", () => {
    mockLocale = "en";
    const { result } = renderHook(() => useMoney());
    expect(result.current(1900, "EUR")).toBe("€19.00");
    expect(result.current(1900, null)).toBe("CA$19.00");
  });
});
