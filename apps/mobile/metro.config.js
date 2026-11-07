// Metro for the mobile client inside the Sovkora pnpm workspace (apps/).
//
// React Native renders with its own copy of React's renderer, which must be the exact React version the app runs:
// React Native 0.86 (Expo SDK 57) ships the 19.2.3 renderer, while the web apps of the workspace run React 19.2.x,
// hoisted at the workspace root. The workspace installs React 19.0.0 for this package only (its `overrides`), and
// every `react` import of the bundle — the app's and its dependencies', hoisted or not — resolves to that copy here.
const path = require("node:path");
const { getDefaultConfig } = require("expo/metro-config");

const config = getDefaultConfig(__dirname);
const fromApp = path.join(__dirname, "package.json");

config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName === "react" || moduleName.startsWith("react/")) {
    return context.resolveRequest({ ...context, originModulePath: fromApp }, moduleName, platform);
  }
  return context.resolveRequest(context, moduleName, platform);
};

module.exports = config;
