/// <reference types="expo/types" />

// Loads Expo's ambient declarations — chiefly `NodeJS.ProcessEnv`, which is how
// `EXPO_PUBLIC_*` variables are typed. Expo inlines them at build time, so this is a
// compile-time declaration only; there is no Node runtime on the device.
//
// Without it `src/config/api.ts` cannot see `process.env` and the app fails its own
// `npm run typecheck` — which nothing was running, so it failed silently.
