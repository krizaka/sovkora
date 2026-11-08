// The iOS 27 SDK stops an app at launch unless it adopts the scene-based life cycle
// (`___UIApplicationEvaluateRuntimeIssueForNoSceneLifecycleAdoption`, EXC_BREAKPOINT). Expo SDK 57 ships the scene
// delegate (`ExpoAppSceneDelegate`, Objective-C name `EXExpoAppSceneDelegate`) but its prebuild template does not use
// it yet; the SDK 58 template does. This plugin applies the SDK 58 template's two changes to the generated project:
//   1. Info.plist declares one window scene whose delegate is Expo's scene delegate;
//   2. AppDelegate provides its React Native factory to that delegate and no longer creates the window itself.
// Remove it when the app moves to an SDK whose template adopts scenes (Expo 58+).
const { withAppDelegate, withInfoPlist } = require("expo/config-plugins");

const START_IN_APP_DELEGATE =
  /\n#if os\(iOS\) \|\| os\(tvOS\)\n\s*window = UIWindow\(frame: UIScreen\.main\.bounds\)\n\s*factory\.startReactNative\([\s\S]*?\)\n#endif\n/;

/** One window scene, delegated to Expo's scene delegate. */
const SCENE_MANIFEST = {
  UIApplicationSupportsMultipleScenes: false,
  UISceneConfigurations: {
    UIWindowSceneSessionRoleApplication: [
      { UISceneConfigurationName: "Default Configuration", UISceneDelegateClassName: "EXExpoAppSceneDelegate" },
    ],
  },
};

/** The SDK 57 AppDelegate.swift, made the factory provider of the scene delegate. Idempotent; throws on another template. */
function adoptScenes(src) {
  if (!src.includes("ExpoReactNativeFactoryProvider")) {
    src = src.replace(
      "class AppDelegate: ExpoAppDelegate {",
      "class AppDelegate: ExpoAppDelegate, ExpoReactNativeFactoryProvider {",
    );
  }
  src = src.replace(
    START_IN_APP_DELEGATE,
    "\n    // The scene delegate creates the window and starts React Native (scene life cycle, iOS 27 SDK).\n",
  );
  if (!src.includes("ExpoReactNativeFactoryProvider") || src.includes("factory.startReactNative(")) {
    throw new Error("with-scene-lifecycle: the AppDelegate template changed — check the plugin against the Expo SDK");
  }
  return src;
}

function withSceneLifecycle(config) {
  config = withInfoPlist(config, (c) => {
    c.modResults.UIApplicationSceneManifest = SCENE_MANIFEST;
    return c;
  });
  return withAppDelegate(config, (c) => {
    if (c.modResults.language !== "swift") throw new Error("with-scene-lifecycle: expected a Swift AppDelegate");
    c.modResults.contents = adoptScenes(c.modResults.contents);
    return c;
  });
}

module.exports = withSceneLifecycle;
module.exports.adoptScenes = adoptScenes;
module.exports.SCENE_MANIFEST = SCENE_MANIFEST;
