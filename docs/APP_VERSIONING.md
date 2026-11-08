# App versioning — compatibility, forced update, contract and OTA

An app (the iPhone and Android client, the Mac app) lives longer than the API it calls. A person on an old build must never
meet a broken screen: they are told, and forced to update when the build can no longer work. This is one contract for every
Krizaka app (krizaka/.github#75), built from three shared pieces:

| Piece | Where | What |
| :--- | :--- | :--- |
| `krizaka-app-config` | krizaka-java `krizaka-service-kit` (Java building block, `krizaka-spring-boot-starter-app-config`) | the endpoint, the `426` filter, the tables, the admin API |
| `@krizaka/app-config` | `krizaka/krizaka-ui` `packages/app-config` | the same contract for Next.js backends and the clients (`/client`, `/react`) |
| `UpdatePrompt`, `ForceUpdateScreen`, `MaintenanceScreen` | `@krizaka/ui` (web) and `@krizaka/ui/native` | the three screens |

## The contract

The app sends `X-Client-Platform` (`ios` · `android` · `macos`…), `X-Client-Version` (`1.4.0`) and `X-Contract-Version` (an
integer) on every request. The backend:

- publishes **`GET /api/v1/app-config?platform=ios&version=1.4.0&locale=fr`** — public, `Cache-Control: public, max-age=60`,
  `ETag`: `minSupportedVersion`, `latestVersion`, `updateMode` (`none` · `soft` · `force`), `storeUrl`, `maintenance`
  `{on, message}`, `contract` `{min, max, sunset}`, `flags`, a localized `message`;
- answers **`426 Upgrade Required`** (Problem Details with the same payload, `updateMode: force`) to a version below the
  minimum or a contract below `contract.min` — before authentication, so an old build with an expired session still learns
  it must update;
- keeps **N-1 contracts**: a contract older than `contract.max` but not below `contract.min` is served with
  `Deprecation: true` and, when announced, `Sunset: <date>`.

Every value is a **database parameter** (`app_config` / `app_config_history` in `orazaka_db`, seeded permissive at version 1):
edited through `PUT /api/v1/admin/app-config/{platform}` with a **reason**, bounded (minimum ≤ latest, contract range not
empty, `https` store URL), audited, rolled back as a new revision (`POST …/rollback/{revision}`). The conversation service
hosts it; the edge routes `/api/v1/app-config` and `/api/v1/admin/app-config` through its catch-all.

## The clients

- **Mobile** (`orazaka-mobile-client`): `src/config/appVersion.ts` creates the client (`@krizaka/app-config/client`) from the
  build's version (`app.json`) and `APP_CONTRACT`; every request goes through `appFetch` (the chat stream, on XHR, sets the
  same headers and reports its 426); `AppVersionGate` (above the session and the navigator) checks at launch and each time
  the app returns to the foreground, then shows the maintenance screen, the forced update or — above the app, dismissible
  once per `latestVersion` — the soft prompt. The words are the operator's (`message`) with the app's as the fallback; the
  store button opens `storeUrl`.
- **Mac app** (Tauri 2, the system WebView): the same package and the web screens (`@krizaka/ui/force-update-screen`…),
  `visibilityResume(document)` for the resume check, `platform: "macos"`.
- A backend that is not Java (Yomizaka…) uses `@krizaka/app-config/server` (`createAppConfigHandler`,
  `checkClientVersion` in its `proxy.ts`), the same tables (`schemaSql`) and the same fixture.

## OTA — auto-resolve, and a store update only when the runtime changes

A JavaScript-only fix goes out over the air with **`expo-updates`**; a store build is needed only when native code changes.

- `app.json`: `runtimeVersion: { policy: "appVersion" }` — the runtime is the app `version`, so an update reaches only builds with
  the same native code. **Any native change (a new Expo module, an SDK bump) means a new `version`** — and a new API contract
  means a new `APP_CONTRACT` in the same release, so the version, the runtime and the contract move together.
- `eas.json`: each build profile has its **channel** (`development`, `preview`, `production`); an update is published to a
  channel and delivered to the builds of that channel with a matching runtime.
- Ship a fix: `eas update --channel preview --message "…"` (then `production`); build: `eas build --profile preview`.
  Nothing is published to the stores from CI; the free EAS tier covers the preview builds and the updates.
- The app fetches the update at launch (`checkAutomatically: ON_LOAD`) and applies it on the next start.

## Runbook

| I want to… | Do |
| :--- | :--- |
| Ask everyone on iOS to update (soft) | admin: `latestVersion` above the installed one, `message` in both languages |
| Retire old builds | admin: `minSupportedVersion` = the oldest build that still works → `426` below it |
| Stop the app for maintenance | admin: `maintenanceOn` + a `maintenanceMessage` in both languages |
| Panic switch | admin: `updateOverride = force` (everyone updates) or `none` (silence the prompts) |
| Announce the end of an old contract | raise `contractMax`, set `contractSunset`; clients on the previous contract get `Deprecation`/`Sunset` |
| Undo a mistake | `POST /api/v1/admin/app-config/{platform}/rollback/{revision}` |
