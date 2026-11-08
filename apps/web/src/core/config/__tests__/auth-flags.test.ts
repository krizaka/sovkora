import {
  githubConfigured,
  googleConfigured,
  providerOffered,
  authClosed,
  isBackendPath,
  parseFlag,
  readAuthFlags,
} from "@/core/config/auth-flags";

describe("auth flags", () => {
  it("are off by default", () => {
    expect(readAuthFlags({})).toMatchObject({ login: false, signup: false });
    expect(authClosed(readAuthFlags({}))).toBe(true);
  });

  it("read AUTH_LOGIN_ENABLED and AUTH_SIGNUP_ENABLED independently", () => {
    expect(readAuthFlags({ AUTH_LOGIN_ENABLED: "true" })).toMatchObject({ login: true, signup: false });
    expect(readAuthFlags({ AUTH_SIGNUP_ENABLED: "1" })).toMatchObject({ login: false, signup: true });
    expect(authClosed(readAuthFlags({ AUTH_LOGIN_ENABLED: "true" }))).toBe(false);
  });

  it("offer an OAuth provider only when it is configured AND sign-up is open (the gateway provisions on first sign-in)", () => {
    const env = { AUTH_LOGIN_ENABLED: "true", GITHUB_ID: "id", GITHUB_SECRET: "secret", GOOGLE_CLIENT_ID: "id" };
    expect(readAuthFlags({ ...env, AUTH_SIGNUP_ENABLED: "true" })).toMatchObject({ github: true, google: false });
    expect(readAuthFlags(env)).toMatchObject({ github: false, google: false });
    expect(readAuthFlags({ GITHUB_ID: "id", GITHUB_SECRET: "s", AUTH_SIGNUP_ENABLED: "true" })).toMatchObject({
      github: false,
    });
    expect(githubConfigured({ GITHUB_ID: "id" })).toBe(false);
    expect(googleConfigured({ GOOGLE_CLIENT_ID: "a", GOOGLE_CLIENT_SECRET: " " })).toBe(false);
    expect(providerOffered(true, false)).toBe(false);
  });

  it("only accept explicit truthy words", () => {
    expect(["true", "TRUE", " 1 ", "yes", "on"].map(parseFlag)).toEqual([true, true, true, true, true]);
    expect(["false", "0", "", "nope", undefined].map(parseFlag)).toEqual([false, false, false, false, false]);
  });

  it("recognise the paths that reach the backend", () => {
    expect(isBackendPath("/api/v1/profile")).toBe(true);
    expect(isBackendPath("/api/auth/session")).toBe(true);
    expect(isBackendPath("/api/chat/stream/1")).toBe(true);
    expect(isBackendPath("/uploads/a.png")).toBe(true);
    expect(isBackendPath("/features/privacy")).toBe(false);
    expect(isBackendPath("/api/auth/register")).toBe(false); // refuses by itself with a 403
  });
});
