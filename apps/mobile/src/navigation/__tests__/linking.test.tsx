import { getStateFromPath } from "@react-navigation/native";
import { linking } from "../linking";

const stateFor = (path: string) => getStateFromPath(path, linking.config);

describe("universal links open the screen that shows the same thing", () => {
  it.each([
    ["/studios", "Studios"],
    ["/chat", "ChatStream"],
    ["/jobs", "Jobs"],
    ["/settings", "Profile"],
  ])("%s opens the %s tab", (path, tab) => {
    const state = stateFor(path);
    expect(state?.routes[0].name).toBe("Tabs");
    const tabs = state?.routes[0].state;
    expect(tabs?.routes.at(-1)?.name).toBe(tab);
  });

  it("opens a Studio from /studios/<key>", () => {
    const route = stateFor("/studios/image")?.routes.at(-1);
    expect(route?.name).toBe("StudioDetail");
    expect(route?.params).toEqual({ studioKey: "image" });
  });

  it("opens a past run from the web's /studios/runs/<id> on the Jobs tab, where runs are listed", () => {
    const tabs = stateFor("/studios/runs/8f2c")?.routes[0].state;
    expect(tabs?.routes.at(-1)?.name).toBe("Jobs");
  });

  it.each([
    ["/settings/integrations", "Connectors"],
    ["/settings/system", "System"],
  ])("opens %s on the %s screen", (path, screen) => {
    expect(stateFor(path)?.routes.at(-1)?.name).toBe(screen);
  });

  it("opens the other settings sections of the web (account, appearance…) on the Settings tab", () => {
    for (const section of ["account", "appearance", "workspace", "api-keys", "webhooks"]) {
      const tabs = stateFor(`/settings/${section}`)?.routes[0].state;
      expect(tabs?.routes.at(-1)?.name).toBe("Profile");
    }
  });

  it("starts a run from orazaka://studios/<key>/run, the label being the key until the Studio says its name", () => {
    const route = stateFor("/studios/image/run")?.routes.at(-1);
    expect(route?.name).toBe("StudioRun");
    expect(route?.params).toEqual({ studioKey: "image" });
  });

  it.each([
    ["/login", "Login"],
    ["/register", "Register"],
    ["/forgot-password", "ForgotPassword"],
  ])("the sign-in flow has an address of its own: %s opens %s", (path, screen) => {
    expect(stateFor(path)?.routes.at(-1)?.name).toBe(screen);
  });

  it("a password-reset link carries its token", () => {
    const route = stateFor("/reset-password/abc123")?.routes.at(-1);
    expect(route?.name).toBe("ResetPassword");
    expect(route?.params).toEqual({ token: "abc123" });
  });

  it("every screen the app can open has an address", () => {
    const declared = new Set<string>();
    const walk = (screens: Record<string, unknown> | undefined) => {
      for (const [name, value] of Object.entries(screens ?? {})) {
        declared.add(name);
        if (value && typeof value === "object" && "screens" in value)
          walk((value as { screens: Record<string, unknown> }).screens);
      }
    };
    walk(linking.config?.screens as Record<string, unknown>);
    for (const screen of [
      "Login",
      "Register",
      "ForgotPassword",
      "ResetPassword",
      "StudioDetail",
      "StudioRun",
      "Connectors",
      "System",
      "Studios",
      "ChatStream",
      "Jobs",
      "Profile",
    ]) {
      expect(declared.has(screen)).toBe(true);
    }
  });

  it("claims the addresses of the web app and ours", () => {
    expect(linking.prefixes).toEqual(["https://www.orazaka.com", "orazaka://"]);
  });
});
