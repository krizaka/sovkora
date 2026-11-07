import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { DEFAULT_BRAND, brandName, withBrand } from "@/core/brand/brand";
import { readBrandName } from "@/core/brand/brand.server";
import { translations, translationsFor } from "@/core/context/translations";

jest.mock("server-only", () => ({}));

const CLOSED = { login: false, signup: false, github: false, google: false };
const OPEN = { ...CLOSED, login: true };
const CONTEXT = path.join(process.cwd(), "src/core/context");

describe("brand name", () => {
  test("the default is the commercial name, a code name is refused", () => {
    expect(DEFAULT_BRAND).toBe("Sovkora");
    expect(brandName("  Sovkora Pro ")).toBe("Sovkora Pro");
    expect(brandName("")).toBe("Sovkora");
    expect(brandName("Orazaka")).toBe("Sovkora");
  });

  test("withBrand fills {brand} deep and leaves other placeholders", () => {
    expect(withBrand({ a: ["Hi {brand}", { b: "{count} on {brand}" }] }, "Nova")).toEqual({
      a: ["Hi Nova", { b: "{count} on Nova" }],
    });
  });
});

describe("messages carry the placeholder, never the typed name", () => {
  const files = readdirSync(CONTEXT).filter((f) => /^translations\..*\.ts$/.test(f) && !f.endsWith(".types.ts"));

  test.each(files)("%s types no product name in a string", (file) => {
    const code = readFileSync(path.join(CONTEXT, file), "utf8")
      .split("\n")
      .filter((line) => !/^\s*(\*|\/\*|\/\/)/.test(line));
    expect(code.filter((line) => /Sovkora|Orazaka/.test(line))).toEqual([]);
  });

  test("the dictionary under the default name reads Sovkora, under another it follows the setting", () => {
    expect(JSON.stringify(translations)).not.toContain("{brand}");
    expect(JSON.stringify(translations.en)).toContain("Sovkora");
    const renamed = JSON.stringify(translationsFor("Nova"));
    expect(renamed).toContain("Nova");
    expect(renamed).not.toContain("Sovkora");
  });
});

describe("readBrandName", () => {
  const answer = (value: unknown, ok = true) => jest.fn().mockResolvedValue({ ok, json: async () => ({ value }) });

  test("a closed site never calls the backend and reads the instance value", async () => {
    const fetcher = answer("Nope");
    expect(await readBrandName(CLOSED, { APP_BRAND_DISPLAY_NAME: "Nova" }, fetcher)).toBe("Nova");
    expect(fetcher).not.toHaveBeenCalled();
  });

  test("an open site reads the setting at request time", async () => {
    const fetcher = answer("Nova Pro");
    expect(await readBrandName(OPEN, {}, fetcher)).toBe("Nova Pro");
    expect(fetcher.mock.calls[0][0]).toContain("/api/v1/settings/public/brand.displayName");
  });

  test("a failing edge falls back to the instance value, then the commercial name", async () => {
    const down = jest.fn().mockRejectedValue(new Error("down"));
    expect(await readBrandName(OPEN, { APP_BRAND_DISPLAY_NAME: "Nova" }, down)).toBe("Nova");
    expect(await readBrandName(OPEN, {}, answer("x", false))).toBe("Sovkora");
  });
});
