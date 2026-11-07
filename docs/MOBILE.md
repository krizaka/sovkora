# 📱 Mobile — the Orazaka app for iOS and Android

The app is [`krizaka/orazaka-mobile-client`](https://github.com/krizaka/orazaka-mobile-client): Expo SDK 53, React
Native 0.79, React Navigation, cloned in the workspace at `apps/mobile`. It talks to the
**edge** (`:8088`) with `Authorization: Bearer` — the same services, the same interceptor pipeline and the same
entitlements as the web client.

What this page describes was **run on 2026-10-12** on the iOS 27 simulator (iPhone 17, Xcode 27) and the Android 16
emulator (Pixel 8 image) against the local Orazaka stack, signed in as the demo persona (Eric, `orazaka demo seed`). Where a screen shows something that does not
come from the server, it says so.

| Platform | State |
| :--- | :--- |
| iOS simulator (iOS 27) | ✅ builds and runs against the local stack — sign-in, Studios, chat, jobs, connectors, profile |
| Android emulator (Android 16) | ✅ development build runs against the local stack — sign-in, Studios, jobs, profile |
| iPhone / store builds | ✅ EAS project `orazaka-mobile` (EAS-managed credentials); iOS builds ship to an internal TestFlight group (App Store Connect record `com.krizaka.orazaka`), never submitted for review; Android: a preview APK (internal distribution). Every profile of `eas.json` points at `https://www.orazaka.com/edge` |
| Expo SDK | ⚠️ 53 — the move to SDK 57 (the other Krizaka apps) is planned; two stopgaps below keep 53 building and launching on Xcode 27 / iOS 27 |

---

## 1. What the app does today

### Sign in

E-mail and password (`POST /api/v1/auth/login`); create an account (`/api/v1/auth/register`, which may ask to verify
the e-mail first), forgot and reset password. The session (token, username, authorities) is kept in the device's
secure storage only (`expo-secure-store`); the navigator shows the app stack only while a session exists.

![Sign in](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/01-sign-in.f80eff4a35.webp)

[![Eric signs in and lands on the Studios (12 s)](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/clip-sign-in-poster.1a2b0990e4.webp)](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/clip-sign-in.7e146c1fcc.mp4)

### Studios

The Studio catalogue (`GET /api/v1/studios`) — locked Studios listed, not hidden, exactly as on the web — then a
Studio's page (`GET /api/v1/studios/<key>`: price per run, install — or "Ouvrir" for a toolkit Studio such as image, video
and voice, which has no installation and runs on its own key, `POST /api/v1/studios/<key>/runs`) and its run screen (the
Studio's input form — an `enum` input such as a size or an aspect is a row of chips, a number is sent as a number — then
the run started and polled: `/api/v1/studios/runs/<id>`). The run screen shows each step's status while it works, then the
outputs **as the blueprint typed them**: a picture shown inline, a clip or a voice-over played with `expo-video` (the edge's
assets are private: the Bearer goes on the request), a rules report (a lease checked against the law) as cards — failures
first, with the passage quoted and the article cited — and a model's text as Markdown (headings, lists, bold, links; never a
literal `**`), the run's own facts (provider, duration) apart from the words.

![The Studio catalogue](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/02-studios.c219e3160e.webp)
![A Studio's page](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/03-studio-detail.d43b3a0d83.webp)

[![Eric runs “Qualification de Prospects” from the phone: the form, the steps running, the results (typing and the model’s wait cut, 28 s)](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/clip-studio-run-poster.0b7a5a32f8.webp)](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/clip-studio-run.032b3d4bc8.mp4)

### Chat

A message is streamed back token by token (Server-Sent Events, `/api/v1/chat/stream/<conversation>`), through the
interceptor pipeline and the model routing of the stack it runs on. Below, Eric's question and the answer of the local
stack. Before the first message the screen shows a presentation card of the sovereign chat — an illustration, not a
conversation.

![A streamed answer, on the local stack](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/09-chat-answer.0bb9fc7895.webp)

[![Eric asks for a note and the answer streams in, from a model on the local stack (24 s)](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/clip-ask-poster.6941d8745f.webp)](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/clip-ask.3ac10ddbf6.mp4)

### Jobs

The account's jobs (`GET /api/v1/jobs`): jobs per day and by kind (charts from `@krizaka/charts`), then each job with
its status and its age.

![Jobs](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/05-jobs.1901326573.webp)

[![Eric's jobs: per day, by kind, then each one (6 s)](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/clip-jobs-poster.232aa9995c.webp)](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/clip-jobs.fda5e4931d.mp4)

### Connectors and profile

**Connecteurs** lists the account's connector credentials (`GET /api/v1/credentials`; none for Eric).
**Profil** reads `GET /api/v1/profile` (name, e-mail, roles) and leads to Abonnement, Console d'intentions
(`POST /api/v1/intent/route`) and Gouvernance, then sign-out.

![Profile](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/08-profile.9c4ac4e531.webp)

### What does not come from the server yet

- **Système** (the "System Entropy" tab) draws fixed values written in the app — it is not a measure of the stack.
- **Gouvernance** lists sample interceptor policies written in the app.
- **Abonnement** shows plan cards written in the app; subscribing happens on the web.

![Système — fixed values, not a measure](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/07-system.7958874204.webp)

On Android (development build, Android 16 emulator):

![The Studios on Android](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/android-studios.f4731b5d1d.webp)
![Jobs on Android](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/android-jobs.958e8b8d92.webp)

A short recording of the smoke run (sign in → a Studio → jobs → profile → sign out):
[ios-smoke.mp4](https://krizaka-assets.b-cdn.net/site/docs/orazaka/mobile/ios-smoke.bb7004733c.mp4).

---

## 2. Run it on a simulator

Requirements: macOS with **Xcode 27** and an iOS simulator runtime, CocoaPods, Node 22, JDK 21 for the stack, and the
workspace cloned (`node scripts/workspace.mjs clone`, then `npm install` in `orazaka-apps/ui`).

```bash
# 1. The stack: infrastructure, then the services the app calls
orazaka start --mode dev
orazaka dev --only edge,identity,studio,job,billing,router
orazaka demo seed                      # the demo persona (local only)

# 2. The app
cd apps/mobile
npx expo run:ios                       # prebuild, pods, build, install, Metro
```

The iOS simulator shares the Mac's network, so the default edge address (`http://localhost:8088`) works. On a phone or
the Android emulator, set `EXPO_PUBLIC_EDGE_URL` (`http://10.0.2.2:8088` for the emulator, then
`npx expo run:android`; a development build allows plain HTTP).

Two stopgaps keep SDK 53 working with today's tools, both to delete with the move to SDK 57:

- **Xcode 27 / iOS 27** — `plugins/with-xcode27.js`: `fmt` (React Native 0.79) compiled in C++17, no pod below
  iOS 15.1, and the scene life cycle iOS 27 requires (an app built with the iOS 27 SDK that does not adopt `UIScene`
  is stopped at launch).
- **React** — React Native renders with its own copy of React's renderer, which must match React exactly: 0.79 ships
  19.0.0 while the web apps of the workspace run 19.2.x. The workspace installs React 19.0.0 for this package only
  (`overrides` in `orazaka-apps/ui/package.json`) and `metro.config.js` resolves every `react` import to it.

---

## 3. Tests

| | What | Where |
| :--- | :--- | :--- |
| Unit | the SSE parser, the API client, the session store, job statistics, Studio input schemas | `src/**/__tests__/*.test.ts` (Jest project `logic`) |
| Render | the app on edge responses recorded as the demo persona: sign-in and its refusal, a stored session, the Studios, the jobs, the profile and sign-out, an unreachable edge | `src/__tests__/app.test.tsx` (Jest project `screens`), fixtures in `src/__fixtures__/api` |
| Smoke | [Maestro](https://maestro.mobile.dev): sign in → a Studio → jobs → profile → sign out, on a simulator | `.maestro/smoke.yaml` |

The repository's CI builds the workspace and runs `npm run validate` (lint, type-check, both Jest projects) on every
pull request. The Maestro flow runs on a Mac with a simulator and the stack up:

```bash
maestro test .maestro/smoke.yaml -e ORAZAKA_EMAIL=<email> -e ORAZAKA_PASSWORD=<password>
```

---

## 4. What is missing

- **Expo SDK 57** — and with it the end of the two stopgaps above.
- **Android**: a run on a physical phone (the preview APK is run on the Android 16 emulator and signs in as the persona on dev).
- **Push notifications**: the APNs key is still manual; no push yet.
- **Words**: the app has an English and a French catalogue (`src/i18n/en.json`, `fr.json`); a few server messages still arrive in the server's language.
- **Système, Gouvernance, Abonnement**: read from the server instead of values written in the app.
- **Theme**: the app follows the device (`userInterfaceStyle: automatic`); the tab bar carries the same @krizaka/icons glyphs as the web rail.
