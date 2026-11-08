import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "@jest/globals";

// The plugin is CommonJS run by Expo's prebuild; its two pure parts are tested here.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { adoptScenes, SCENE_MANIFEST } = require("../with-scene-lifecycle") as {
  adoptScenes: (src: string) => string;
  SCENE_MANIFEST: {
    UISceneConfigurations: { UIWindowSceneSessionRoleApplication: { UISceneDelegateClassName: string }[] };
  };
};

/** The AppDelegate.swift of expo-template-bare-minimum@57.0.29, what `expo prebuild` generates for this SDK. */
const template = fs.readFileSync(path.join(__dirname, "AppDelegate.sdk57.swift.txt"), "utf8");

describe("with-scene-lifecycle (iOS 27 requires the scene life cycle)", () => {
  it("starts with an AppDelegate that creates its own window — the app iOS 27 stops at launch", () => {
    expect(template).toContain("factory.startReactNative(");
    expect(template).not.toContain("ExpoReactNativeFactoryProvider");
  });

  it("makes the AppDelegate the factory provider and leaves the window to the scene delegate", () => {
    const patched = adoptScenes(template);
    expect(patched).toContain("class AppDelegate: ExpoAppDelegate, ExpoReactNativeFactoryProvider {");
    expect(patched).not.toContain("factory.startReactNative(");
    expect(patched).not.toContain("UIWindow(frame:");
    expect(patched).toContain("reactNativeFactory = factory");
  });

  it("is idempotent", () => {
    const once = adoptScenes(template);
    expect(adoptScenes(once)).toBe(once);
  });

  it("refuses a template it does not know rather than build an app that stops at launch", () => {
    expect(() => adoptScenes("class AppDelegate: SomethingElse {}")).toThrow(/template changed/);
  });

  it("declares one window scene delegated to Expo's scene delegate", () => {
    expect(SCENE_MANIFEST.UISceneConfigurations.UIWindowSceneSessionRoleApplication[0].UISceneDelegateClassName).toBe(
      "EXExpoAppSceneDelegate",
    );
  });
});
