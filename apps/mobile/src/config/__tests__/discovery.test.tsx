import React from "react";
import { generateKeyPairSync } from "crypto";
import { readFileSync } from "fs";
import { join } from "path";
import { render, screen, waitFor } from "@testing-library/react-native";
import { Text } from "react-native";
import { ThemeProvider } from "@krizaka/ui/native";
import { DiscoveryProvider } from "@krizaka/discovery/react";
import { signManifest } from "@krizaka/discovery/sign";
import type { ManifestBody, ServiceEntry } from "@krizaka/discovery";
import { DiscoveryGate } from "../../components/DiscoveryGate";
import { AuthProvider } from "../../core/AuthContext";
import { SystemScreen } from "../../screens/SystemScreen";
import { answerFetch } from "../../__tests__/test-api";
import { LocaleProvider } from "../../i18n";
import { apiRequest, DEV_EDGE_URL, edgeBase, edgeUrl, setEdgeBase } from "../api";
import {
  appEnvironmentFrom,
  BAKED_ADDRESSES,
  createAppDiscovery,
  discovery,
  edgeOverrideFrom,
  LEGACY_ADDRESSES,
  orazakaWebUrl,
  UPDATE_URL,
} from "../discovery";

// krizaka-internal#2684: the screens draw the commercial mark; the code-named one never appears.
jest.mock("@krizaka/ui/native", () => {
  const { View } = jest.requireActual("react-native");
  const actual = jest.requireActual("@krizaka/ui/native");
  return {
    ...actual,
    SovkoraMark: () => <View testID="sovkora-mark" />,
    OrazakaMark: () => <View testID="orazaka-mark" />,
  };
});

jest.mock("expo-secure-store", () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => undefined),
  deleteItemAsync: jest.fn(async () => undefined),
}));

const root = join(__dirname, "../../..");
const key = (() => {
  const { privateKey, publicKey } = generateKeyPairSync("ed25519");
  return {
    seed: privateKey.export({ format: "der", type: "pkcs8" }).subarray(-32).toString("base64"),
    publicKey: publicKey.export({ format: "der", type: "spki" }).subarray(-32).toString("base64"),
  };
})();

const entry = (over: Partial<ServiceEntry> = {}): ServiceEntry => ({
  apiBase: "https://www.orazaka.com/edge",
  webBase: "https://www.orazaka.com",
  minAppVersion: "1.0.0",
  latestAppVersion: "1.0.0",
  status: "ok",
  ...over,
});
const manifest = (over: Partial<ServiceEntry> = {}, issuedAt = "2026-10-21T12:00:00Z") => {
  const body: ManifestBody = { schemaVersion: 1, keyId: "k1", issuedAt, services: { orazaka: { dev: entry(over) } } };
  return JSON.stringify(signManifest(body, key.seed));
};
const memory = () => {
  const data = new Map<string, string>();
  return {
    getItem: async (k: string) => data.get(k) ?? null,
    setItem: async (k: string, v: string) => void data.set(k, v),
    removeItem: async (k: string) => void data.delete(k),
  };
};
const serving = (get: () => string) =>
  (async () => ({ ok: true, status: 200, text: async () => get() })) as unknown as typeof fetch;
const make = (get: () => string, appVersion = "1.0.0") =>
  createAppDiscovery({ fetch: serving(get), publicKeys: { k1: key.publicKey }, storage: memory(), appVersion });

describe("the baked addresses", () => {
  it("are the dev edge and the dev website until the manifest says otherwise", () => {
    expect(BAKED_ADDRESSES).toEqual({ apiBase: DEV_EDGE_URL, webBase: "https://www.orazaka.com" });
    expect(LEGACY_ADDRESSES).toEqual(BAKED_ADDRESSES);
    expect(edgeBase()).toBe(DEV_EDGE_URL);
    expect(orazakaWebUrl()).toBe("https://www.orazaka.com");
    expect(discovery.getService("orazaka").source).toBe("default");
  });
  it("send the update screen to TestFlight and the EAS builds while the app is not in the stores", () => {
    expect(UPDATE_URL.ios).toBe("itms-beta://");
    const app = JSON.parse(readFileSync(join(root, "app.json"), "utf8")) as { expo: { owner: string; slug: string } };
    expect(UPDATE_URL.android).toBe(`https://expo.dev/accounts/${app.expo.owner}/projects/${app.expo.slug}/builds`);
  });
});

describe("the build's inputs", () => {
  it("takes an edge for local work, refuses a malformed one, and trims the slash", () => {
    expect(edgeOverrideFrom(undefined)).toBeUndefined();
    expect(edgeOverrideFrom("")).toBeUndefined();
    expect(edgeOverrideFrom("http://10.0.2.2:8088/")).toBe("http://10.0.2.2:8088");
    expect(() => edgeOverrideFrom("10.0.2.2:8088")).toThrow("EXPO_PUBLIC_EDGE_URL");
    expect(() => edgeOverrideFrom("ftp://x")).toThrow("EXPO_PUBLIC_EDGE_URL");
  });
  it("follows the dev entry unless told otherwise, and refuses another channel", () => {
    expect(appEnvironmentFrom(undefined)).toBe("dev");
    expect(appEnvironmentFrom("prod")).toBe("prod");
    expect(() => appEnvironmentFrom("qa")).toThrow("EXPO_PUBLIC_APP_ENV");
  });
});

describe("the live switch: a manifest edit moves the app without a rebuild", () => {
  it("moves the website address (webBase) and the edge address when the manifest is re-issued", async () => {
    let current = manifest();
    const d = make(() => current);
    await d.start();
    expect(d.getService("orazaka")).toMatchObject({ webBase: "https://www.orazaka.com", source: "live" });
    current = manifest(
      { webBase: "https://www.orazaka.com", apiBase: "https://api.orazaka.com/edge" },
      "2026-10-21T13:00:00Z",
    );
    await d.refresh();
    expect(d.getService("orazaka")).toMatchObject({
      webBase: "https://www.orazaka.com",
      apiBase: "https://api.orazaka.com/edge",
    });
  });

  it("refuses an address off the allow-list even when signed", async () => {
    const d = make(() => manifest({ apiBase: "https://attacker.example.net/edge" }));
    await d.start();
    expect(d.getService("orazaka")).toMatchObject({ apiBase: DEV_EDGE_URL, source: "default" });
  });

  it("makes every request go to the edge discovery gives at that moment", async () => {
    const fetchMock = jest.fn().mockResolvedValue({ ok: true, status: 200, text: () => Promise.resolve("{}") });
    (globalThis as { fetch: unknown }).fetch = fetchMock;
    try {
      setEdgeBase(() => "https://api.orazaka.com/edge/");
      await apiRequest("/api/v1/profile");
      setEdgeBase(() => "https://other.orazaka.com/edge");
      await apiRequest("/api/v1/profile");
    } finally {
      setEdgeBase(() => discovery.getService("orazaka").apiBase);
    }
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual([
      "https://api.orazaka.com/edge/api/v1/profile",
      "https://other.orazaka.com/edge/api/v1/profile",
    ]);
    expect(edgeUrl("api/v1/studios")).toBe(`${DEV_EDGE_URL}/api/v1/studios`);
  });
});

describe("the gate the manifest raises", () => {
  const shown = async (get: () => string, appVersion = "1.0.0") => {
    const d = make(get, appVersion);
    await render(
      <ThemeProvider>
        <LocaleProvider>
          <DiscoveryProvider discovery={d} refreshEveryMs={0}>
            <DiscoveryGate>
              <Text>the app</Text>
            </DiscoveryGate>
          </DiscoveryProvider>
        </LocaleProvider>
      </ThemeProvider>,
    );
  };

  it("shows the app when the build is current", async () => {
    await shown(() => manifest());
    expect(await screen.findByText("the app")).toBeTruthy();
  });

  it("replaces the app with the update screen below minAppVersion, with the manifest's message", async () => {
    await shown(() =>
      manifest({
        minAppVersion: "2.0.0",
        latestAppVersion: "2.1.0",
        message: { en: "Please update.", fr: "Mettez à jour." },
      }),
    );
    expect(await screen.findByText("Update Sovkora to continue")).toBeTruthy();
    expect(screen.getByText("Please update.")).toBeTruthy();
    expect(screen.getByText("Version 2.1.0")).toBeTruthy();
    expect(screen.queryByText("the app")).toBeNull();
    expect(screen.getByTestId("sovkora-mark", { includeHiddenElements: true })).toBeTruthy();
    expect(screen.queryByTestId("orazaka-mark", { includeHiddenElements: true })).toBeNull();
  });

  it("replaces the app with the maintenance screen and its message", async () => {
    await shown(() => manifest({ status: "maintenance", message: { en: "Back at noon.", fr: "De retour à midi." } }));
    await waitFor(() => expect(screen.getByText("Sovkora is under maintenance")).toBeTruthy());
    expect(screen.getByText("Back at noon.")).toBeTruthy();
  });
});

describe("the System screen tells where the addresses come from", () => {
  it("shows the edge, the website and the source of both (the built-in default before any manifest)", async () => {
    answerFetch({ "/api/v1/profile": {} });
    await render(
      <ThemeProvider>
        <LocaleProvider>
          <AuthProvider>
            <SystemScreen navigation={{ goBack: jest.fn() } as never} route={{} as never} />
          </AuthProvider>
        </LocaleProvider>
      </ThemeProvider>,
    );
    expect(await screen.findByText("Website")).toBeTruthy();
    expect(screen.getAllByText("www.orazaka.com")).toHaveLength(2);
    expect(screen.getByText("Addresses from")).toBeTruthy();
    expect(screen.getByText("Built-in default")).toBeTruthy();
  });
});
