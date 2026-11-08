/**
 * Jest for the mobile client — two projects.
 *
 * `logic`: Node with `ts-jest` for what touches no native module (the SSE parser, the API client, the session store):
 * loading Expo's native runtime to test a string parser buys nothing.
 *
 * `screens`: the `jest-expo` preset with @testing-library/react-native — screens and the navigator rendered on API
 * responses recorded from the local Orazaka stack (`src/__fixtures__/api`, the demo persona). React is mapped to this
 * package's own copy (React 19.2.3, the version of React Native 0.86 renderer — see metro.config.js), never the
 * workspace's hoisted 19.2.x.
 */
const path = require("node:path");

const reactDir = path.dirname(require.resolve("react/package.json", { paths: [__dirname] }));
/** @krizaka/* packages export ES modules only (an `import` condition): Jest requires their files directly. */
const krizaka = (name, file) =>
  path.join(path.dirname(require.resolve(`@krizaka/${name}/package.json`, { paths: [__dirname] })), file);

/** The ES-module entries of the packages the kit and the app import (each has only an `import` condition). */
const esmEntries = {
  "app-config": ["", "react", "client"],
  pacer: ["", "react"],
  discovery: ["", "react", "sign"],
  intl: ["", "plural", "number", "date", "money"],
  i18n: ["", "react"],
  icons: ["native"],
};
const esmMap = Object.fromEntries(
  Object.entries(esmEntries).flatMap(([name, subs]) =>
    subs.map((sub) => {
      const dir = path.dirname(require.resolve(`@krizaka/${name}/package.json`, { paths: [__dirname] }));
      const entry = require(path.join(dir, "package.json")).exports[sub === "" ? "." : `./${sub}`].import;
      const suffix = sub === "" ? "" : `/${sub}`;
      return [`^@krizaka/${name}${suffix}$`, path.join(dir, entry)];
    }),
  ),
);

module.exports = {
  projects: [
    {
      displayName: "logic",
      testEnvironment: "node",
      preset: "ts-jest",
      testMatch: ["<rootDir>/src/**/__tests__/**/*.test.ts", "<rootDir>/plugins/__tests__/*.test.ts"],
      transform: {
        "^.+\\.tsx?$": ["ts-jest", { tsconfig: { jsx: "react-jsx", esModuleInterop: true, rootDir: __dirname } }],
      },
    },
    {
      displayName: "screens",
      preset: "jest-expo/ios",
      // Like Metro (expo/metro-config): this package's own node_modules first, for hoisted packages too.
      modulePaths: ["<rootDir>/node_modules"],
      setupFiles: ["<rootDir>/jest.setup.screens.js"],
      testMatch: ["<rootDir>/src/**/__tests__/**/*.test.tsx"],
      moduleNameMapper: {
        "^react$": reactDir,
        "^react/(.*)$": `${reactDir}/$1`,
        "^@krizaka/ui/native$": krizaka("ui", "dist/native.js"),
        "^@krizaka/ui/native/(.*)$": krizaka("ui", "dist/native/$1.js"),
        "^@krizaka/tokens/native$": krizaka("tokens", "dist/native.js"),
        "^@krizaka/charts/native$": krizaka("charts", "dist/native.js"),
        ...esmMap,
      },
      transformIgnorePatterns: [
        "node_modules/(?!((jest-)?react-native|@react-native(-community)?|expo(nent)?|@expo(nent)?/.*|@react-navigation/.*|react-navigation|@krizaka/.*|@noble/.*|@tanstack/.*|react-native-svg))",
      ],
    },
  ],
  collectCoverageFrom: ["src/core/**/*.ts", "src/config/**/*.ts"],
};
