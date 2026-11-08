import { readFileSync } from "fs";
import { join } from "path";

/**
 * A fitness function over the session store, in the spirit of the backend's `SqlBoundaryRules`.
 *
 * The property it protects cannot be observed from a unit test: both storages have the same API,
 * so a swap to `AsyncStorage` would keep every behavioural test green while moving the bearer
 * token from the device keychain to plaintext on disk. The only place that difference is visible
 * is the import — so that is what is asserted.
 */
describe("session storage", () => {
  const source = readFileSync(join(__dirname, "..", "AuthContext.tsx"), "utf8");

  it("keeps the JWT in the device keychain", () => {
    expect(source).toContain('from "expo-secure-store"');
  });

  it("never falls back to AsyncStorage — a bearer token is a credential, not a preference", () => {
    // Matched on the import, not on the word: the file *explains* in prose why AsyncStorage is
    // the wrong home for a token, and a check that cannot tell the rationale from the code would
    // fail on its own documentation.
    const imports = source.match(/^import .*$/gm) ?? [];

    expect(imports.some((line) => line.includes("async-storage"))).toBe(false);
  });

  it("clears the stored session on sign-out rather than only dropping it from memory", () => {
    // Without the delete, signing out leaves the token on the device and the next cold start
    // restores the session the actor believed they had ended.
    expect(source).toContain("deleteItemAsync");
  });
});
