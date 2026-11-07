/** @jest-environment node */
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { NextRequest } from "next/server";
import robots from "@/app/robots";
import sitemap from "@/app/sitemap";
import { CLOSED_ROBOTS, devRedirect, indexable, isDevHost, NOINDEX_HEADER } from "@/core/config/indexing";
import { proxy } from "@/proxy";
import { landing } from "@/core/context/translations.landing";
// eslint-disable-next-line no-restricted-imports -- the shared family guard lives in scripts/, outside src/
import { auditRobots } from "../../scripts/check-seo-family.mjs";

const withFlag = <T>(value: string | undefined, run: () => T): T => {
  const before = process.env.APP_SEARCH_INDEXING;
  if (value === undefined) delete process.env.APP_SEARCH_INDEXING;
  else process.env.APP_SEARCH_INDEXING = value;
  try {
    return run();
  } finally {
    if (before === undefined) delete process.env.APP_SEARCH_INDEXING;
    else process.env.APP_SEARCH_INDEXING = before;
  }
};
const ask = (path: string) => proxy(new NextRequest(`https://dev.orazaka.com${path}`));

describe("APP_SEARCH_INDEXING — one flag closes the deployment to search engines", () => {
  it("is open by default, closed only by off", () => {
    expect(indexable({})).toBe(true);
    expect(indexable({ APP_SEARCH_INDEXING: "OFF" })).toBe(false);
  });

  it("off: noindex on every response — pages, login, the BFF (404 or not), robots", () => {
    withFlag("off", () => {
      for (const path of [
        "/",
        "/docs",
        "/login",
        "/dashboard",
        "/api/v1/profile",
        "/api/auth/session",
        "/robots.txt",
      ]) {
        expect(`${path} ${ask(path).headers.get("x-robots-tag")}`).toBe(`${path} ${NOINDEX_HEADER}`);
      }
    });
  });

  it("on: nothing is added", () => {
    withFlag(undefined, () => expect(ask("/").headers.get("x-robots-tag")).toBeNull());
  });

  it("off: robots.txt disallows everything and names no sitemap; the sitemap is empty", () => {
    withFlag("off", () => {
      const r = robots();
      expect(r.sitemap).toBeUndefined();
      expect(r.rules).toEqual([{ userAgent: "*", disallow: "/" }]);
      expect(auditRobots(CLOSED_ROBOTS, { host: "https://dev.orazaka.com", index: false })).toEqual([]);
      expect(sitemap()).toEqual([]);
    });
    withFlag(undefined, () => expect(sitemap().length).toBeGreaterThan(0));
  });

  it("the development-version banner has the words of yomizaka.com in both languages (krizaka-internal#211)", () => {
    expect(landing.en.stage).toEqual({
      title: "Development version",
      message: "Data can be reset at any time. This is not a commercial offer yet.",
      codeName: "code name: {code}",
    });
    expect(landing.fr.stage).toEqual({
      title: "Version de développement",
      message: "Les données peuvent être réinitialisées à tout moment. Ce n'est pas encore une offre commerciale.",
      codeName: "nom de code : {code}",
    });
  });

  it("the banner is @krizaka/ui's EnvironmentNotice, mounted once by the public shell above the page", () => {
    const shell = readFileSync(join(process.cwd(), "src/components/layout/PublicShell.tsx"), "utf8");
    expect(shell).toMatch(
      /<StageNotice show=\{showDevNotice\} \/>\s*<CodeNameProvider dev=\{showDevNotice\}>\{children\}<\/CodeNameProvider>/,
    );
    const notice = readFileSync(join(process.cwd(), "src/components/StageNotice.tsx"), "utf8");
    expect(notice).toContain('from "@krizaka/ui/environment-notice"');
  });
});

describe("dev.orazaka.com sends its pages to www; the BFF, auth and .well-known stay (epic krizaka/.github#119)", () => {
  const withEnv = async <T>(run: () => T | Promise<T>): Promise<T> => {
    const keep = { dev: process.env.APP_DEV_HOST, url: process.env.APP_PUBLIC_URL };
    process.env.APP_DEV_HOST = "dev.orazaka.com";
    process.env.APP_PUBLIC_URL = "https://www.orazaka.com";
    try {
      return await run();
    } finally {
      for (const [k, v] of [
        ["APP_DEV_HOST", keep.dev],
        ["APP_PUBLIC_URL", keep.url],
      ] as const) {
        if (v === undefined) delete process.env[k];
        else process.env[k] = v;
      }
    }
  };
  const dev = (path: string, method = "GET") =>
    proxy(new NextRequest(`https://dev.orazaka.com${path}`, { method, headers: { host: "dev.orazaka.com" } }));
  const www = (path: string) =>
    proxy(new NextRequest(`https://www.orazaka.com${path}`, { headers: { host: "www.orazaka.com" } }));

  it("knows the dev host and what stays", () => {
    expect(isDevHost("dev.orazaka.com", { APP_DEV_HOST: "dev.orazaka.com" })).toBe(true);
    expect(isDevHost("www.orazaka.com", { APP_DEV_HOST: "dev.orazaka.com" })).toBe(false);
    expect(isDevHost("dev.orazaka.com", {})).toBe(false);
    expect(devRedirect("GET", "/studios/1", "?a=b", "https://www.orazaka.com")).toBe(
      "https://www.orazaka.com/studios/1?a=b",
    );
    expect(devRedirect("POST", "/login", "", "https://www.orazaka.com")).toBeNull();
    for (const keep of [
      "/api/auth/callback/credentials",
      "/api/v1/profile",
      "/.well-known/assetlinks.json",
      "/api/healthz",
      "/robots.txt",
    ])
      expect(`${keep} ${devRedirect("GET", keep, "", "https://www.orazaka.com")}`).toBe(`${keep} null`);
  });

  it("dev: a page answers 308 to the same path on www, noindex, even with the flag on", async () => {
    await withEnv(() => {
      const res = dev("/studios?x=1");
      expect(res.status).toBe(308);
      expect(res.headers.get("location")).toBe("https://www.orazaka.com/studios?x=1");
      expect(res.headers.get("x-robots-tag")).toBe(NOINDEX_HEADER);
    });
  });

  it("dev: robots.txt is Disallow: /; the BFF, auth and .well-known keep serving, with noindex", async () => {
    await withEnv(async () => {
      expect(await dev("/robots.txt").text()).toBe(CLOSED_ROBOTS);
      for (const path of ["/api/auth/session", "/api/v1/profile", "/.well-known/assetlinks.json", "/api/healthz"]) {
        const res = dev(path);
        expect(`${path} ${res.headers.get("location")}`).toBe(`${path} null`);
        expect(`${path} ${res.headers.get("x-robots-tag")}`).toBe(`${path} ${NOINDEX_HEADER}`);
      }
    });
  });

  it("www is the public address: not redirected; a code-name host, it says noindex (wave 1, krizaka-internal#2630)", async () => {
    await withEnv(() => {
      const res = www("/docs");
      expect(res.headers.get("location")).toBeNull();
      expect(res.headers.get("x-robots-tag")).toBe(NOINDEX_HEADER);
    });
  });
});
