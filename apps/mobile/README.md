# Sovkora mobile app

> `apps/mobile` of [Sovkora](../../README.md), one repository (pnpm, app layer). Depends on nothing else of Sovkora. Licence: [FSL-1.1-ALv2](../../LICENSE).

Expo / React Native client of the Sovkora platform (talks to the edge with Bearer tokens).

## What it provides

The Expo SDK 53 client (port `8081`). Native clients call the edge (`:8088`) with
`Authorization: Bearer`, not the BFF. Shares tokens and types with the web via
[@krizaka/orazaka-shared](https://github.com/krizaka/orazaka-ui-kit).

```bash
npm install && npm start
```

The app does not hard-code where the edge is: at start it reads the **signed manifest**
`https://www.krizaka.com/.well-known/krizaka/services.json` ([`@krizaka/discovery`](https://ui.krizaka.com/packages/discovery),
[ADR-003](https://github.com/krizaka/.github/blob/main/docs/adr/ADR-003-mobile-service-discovery.md)); a move of the edge is a
manifest edit and a krizaka.com deploy, never a store release. Offline it falls back to its cache, then to the baked dev edge
(`https://www.sovkora.com/edge` — the edge, **not** the website: `https://www.sovkora.com` is the Next.js BFF and answers a
bearer client `401 Missing active security context`).

To run against the local stack, override discovery for your build with an edge the device can reach:

```bash
EXPO_PUBLIC_EDGE_URL=http://192.168.1.5:8088 npx expo run:ios      # http://10.0.2.2:8088 for the Android emulator
```

No EAS profile sets an address; they only set `EXPO_PUBLIC_APP_ENV=dev` (which entry of the manifest the build follows).

Running it on a simulator against the local stack, what each screen does today and what is missing:
[docs/MOBILE.md](https://github.com/krizaka/orazaka/blob/main/docs/MOBILE.md) of the workspace.

## Build and test

```bash
pnpm install                      # from the repository root: every app, each with its own lockfile
pnpm --dir apps/mobile run validate    # lint, types and tests, as CI runs them
```

CI runs it on every pull request that touches `apps/mobile/` (ADR-029: one repository, one CI).
