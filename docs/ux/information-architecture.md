# Sovkora — information architecture

> How Sovkora's menus are organised on every surface, who each one serves, and why. One structure, the same words on
> the public site, the signed-in web app, the phone and the Mac. Feedback:
> [Discussion #182](https://github.com/krizaka/orazaka/discussions/182).
>
> The public bar follows the **product-site navigation standard** every Krizaka product shares (decided 2026-10-15):
> logo · **Product ▾** · **Platforms ▾** · **Docs** · (Pricing when it exists), the tools on the right, the same groups
> in the phone panel and the footer. This document applies it to Sovkora and adds the signed-in app.

## 1. Who comes, and what for

| Audience | Arrives on | Top jobs | Where the answer is |
| :--- | :--- | :--- | :--- |
| **Evaluator** — a developer or an ops lead at a firm judging a self-hosted, sovereign AI engine | the public site, from a referral, LinkedIn or GitHub | *What does it do? Does it run on our hardware? Which models? Is it real?* | Product ▾ (How it works, Models, Demo) · Platforms ▾ (Self-host) · Docs |
| **Business user** — a firm that runs Studios and packs (contract review, Loi 25, image and video) | the public site, then the app | *What can it do for my trade? What does a run cost?* then *run it, follow it, get the result* | Product ▾ (Studios, Packs) → the app: Studios, Jobs, Usage & credits |
| **Krizaka's own products** — Yomizaka and Akinaza using Sovkora as a service | the docs (API, packs, CLI) | *How do I call it, with which pack, under which limits?* | Docs |
| **Signed-in user** (today: QA accounts only, sign-up closed) | the app | chat, run a Studio, follow jobs, install packs, watch usage and credits, change settings | the rail: Home · Studios · Chat · Jobs · Packs · Usage & credits · Settings |

Sources: the landing and docs issues of `orazaka-web-client` (#72, #74, #75, #85, #88, #89, #90), the pack catalogue work
(`orazaka-packs#16`), the locomotive decision (Sovkora for Québec firms and SMEs, Yomizaka as the showcase).

## 2. The public site — dev.sovkora.com

### Before (2026-10-16)

```
Sovkora  [dev · v1.0.0+49d214f]  Product ▾  Demo  Mobile  Documentation      GitHub  EN FR  theme  Sign in  [Talk to us]
                                 ├ How it works
                                 ├ Studios
                                 ├ Packs
                                 ├ Models
                                 └ Mac app
```

Problems: six top-level things of different kinds (a version, a menu, a demo, a platform, the docs); Demo, Mobile and
the Mac app split across the menu and the bar; a version chip that means nothing to a visitor; a 480 × 380 px panel of
long sentences over the hero; the light of « Documentation » stayed on while the Product menu was open (two items lit).

### After

```
Sovkora   Product ▾   Platforms ▾   Docs                    GitHub  EN FR  theme  Sign in  [Talk to us]
          │           ├ Web          → the workspace in the browser (#platform)
          │           ├ Mobile       → /docs/mobile
          │           ├ Mac          → /desktop
          │           └ Self-host    → /docs/self-hosting (→ /infrastructure when that page ships)
          ├ How it works  (#how; the /features/* pages belong here)
          ├ Studios       (#studios)
          ├ Packs         (#packs)
          ├ Models        (/models)
          └ Demo          (/gallery — real runs, nothing staged)
footer: Product · Platforms · Docs · Legal — and « A Krizaka product », DEV · version, Montréal
```

- **Grouped by the visitor's question, not by our nouns**: *what does it do?* (Product), *where does it run?*
  (Platforms), *how do I use it?* (Docs). Pricing joins as a fourth entry when there are prices; until then, Packs
  (with their price before every run) answers it.
- **Models stay in Product**: a model is part of what Sovkora does for you (the standard lists it there); *where it
  runs* is the hardware, which is Platforms › Self-host.
- **Demo is in Product**: it is the proof of the product story, not a destination of its own.
- **The version is in the footer** (`DEV · vX.Y.Z`), never in the bar.
- **One item lit**: the group that holds the page (Product on /models, /gallery, /features/*; Platforms on /desktop,
  /docs/mobile, /docs/self-hosting), Docs for the rest of /docs, nothing on the home (its sections are anchors).
- **The menu is compact**: icon · title · one line (≤ 45 characters), 20 rem wide; it opens on click, keyboard, or a
  pointer that rests 200 ms (passing over the bar opens nothing); moving on to Docs closes it; Escape and a click
  outside close it (`@krizaka/ui` NavBar).
- **Phone**: one panel, the same two groups with their headings, then Docs, the language and theme, Sign in and the one
  action. A press outside closes it.
- **No URL changed**, so no redirect is needed.

Source of truth: `orazaka-web-client/src/features/landing/utils/site-nav.ts` (the bar, the panel and the footer read it);
test: `LandingNav.test.tsx` (structure snapshot, one item lit per route, axe on the bar and an open menu, the panel).

## 3. The signed-in app — web, phone, Mac

### Before

```
rail:   [mark] Dashboard · Chat Sessions · Studios · Packs · Task History · Credits · (Admin Panel) … Log out
header: [mark] Sovkora  [sovereign]                    EN▾  theme  bell  [name + avatar → /profile]
palette (⌘K): the same list again, in its own words
phone tabs: Studios · Chat · Jobs · Profile
```

Problems: three names for the same places (Chat Sessions / Chat, Task History / Jobs, Credits / Usage, Profile /
Settings); the rail, the palette and the phone each with their own list; Sign out on the rail, the account behind a bare
link; the language as a third control in the header.

### After

```
rail (desktop) / drawer (phone web):
  Home · Studios · Chat · Jobs · Plan · Usage & credits
  ───
  Settings · (Admin, admins only)
header: [≡ phone] [mark] Sovkora  [sovereign]                 theme  bell  [avatar ▾]
                                                                           ├ avatar · name · e-mail
                                                                           ├ Language: English / Français
                                                                           └ Sign out
⌘K palette: the rail's sections, same words, same order
phone tabs (Expo):  Studios · Chat · Jobs · Settings
Mac (Tauri): embeds the web client — the same rail and user menu
```

- **One list** — `orazaka-web-client/src/core/config/app-sections.ts` — read by the rail, the drawer and the palette;
  the phone's tabs are the same list without Home, Plan and Usage, in the same order and words.
- **Where you work on the rail, who you are under the avatar**: nothing appears in both (Settings is on the rail, Sign
  out only in the user menu).
- **Gallery is not a section**: the results of your runs are in Jobs (its gallery view); one concept, one place.
- **Automation** (`/automation`, connectors and approvals) is reachable by URL only today; it joins the rail (or
  Settings › Connectors, as on the phone) when it leaves preview — a decision for the automation work, not for this pass.
- **URLs kept** (`/dashboard/jobs`, `/billing`, `/profile`): renaming them to `/jobs`, `/usage`, `/settings` with 308s is
  a follow-up once the run-progress work on the Jobs page (#85/#86) has landed, to avoid two sessions moving the same
  files.

Tests: `Sidebar.test.tsx` (order snapshot, one section current per route, no Sign out on the rail),
`Header.test.tsx` (avatar first, the menu's content, nothing the rail holds), `AppCommandPalette.test.tsx`,
the phone's `app.test.tsx` / `app.fr.test.tsx` (the Settings tab).

### Studios and « Plan & packs » (2026-10-20)

What is true in the code: a **plan** (krizaka-billing) grants monthly credits and `studio.*` entitlements; a **pack**
(the Studio catalogue, priced by billing) bundles Studios, a toolkit pack is had through the plan, a vertical pack is
added to the account; a **Studio** is open when billing or an operator grant allows its entitlement key; an
**installation** exists for vertical Studios only, pinning a version and keeping the Studio's settings. For one person
a Studio is in one of four states, all decided by the server: Ready, Needs a pack, Not on your plan, Not checked
(billing down).

```
/studios  [Free plan · 500 credits a month · 28 Studios ready · 5 more …          Plan & packs]
          [Search 33 Studios      ] [All 33 | Ready 28 | Get more 5] [All trades ▾]
          Creative 8
            Golden hour          Included in your plan · …        Ready          [Run]
            Creator suggestions  Data & analytics pack, CA$49.00  Needs a pack   [Get pack] → drawer ?pack=
          General 14 …
/packs    Plan & packs: Your plan · Your packs · More packs, one line each, Details → the same drawer
```

- **One list, plan on top**: the three questions in order on one screen (what my plan gives, what runs now, what else
  exists and how to get it), filters in the address (`?q=`, `?show=`, `?trade=`, `?pack=`).
- **No Install step**: a vertical Studio is installed by its first run; only a Studio whose blueprint declares settings
  asks them once (« Set up »). Upgrades stay explicit (ADR-034 §5).
- Mockups and the option not taken (two tabs, Ready / Get more grouped by pack): the PR orazaka-web-client#192.

## 4. The home page — one story in six sections

Oussama: « la page doit être plus simplifiée, trop de choses, et je ne sais pas s'il y a une logique ». The home had
**12 sections** (hero · Made here · why · platform · comparison · how it works · phone · Mac · Studios · packs ·
models · closing), several answering the same question, and **12,087 px** of scroll on a desktop (16,219 px on a phone).

The story is now the order of a visitor's questions, one section each, one primary action each:

| # | Question | Section | Proof on screen | Action |
| :- | :--- | :--- | :--- | :--- |
| 1 | What is it? | Hero (`LandingHero`) | a real chat answer, local | Sign in / Docs |
| 2 | What can I do with it? | `#made` (`GalleryFeatured` + `LandingOutcomes`) | the prompt → image → video showcase; four Studio outcomes (`#studios`); one line for the packs (`#packs`) | See everything it made |
| 3 | Where does it run? | `#how` (`LandingRuns`) | tabs Web · Mobile · Mac, the real app recorded in its frame (`StepShowcase`, @krizaka/ui) | the phone guide, Sovkora for Mac |
| 4 | Why own it? | `#why` (`LandingCompare`) | `ComparisonTable`: four questions, aligned rows, Sovkora's band | — |
| 5 | Can I trust it? | `#trust` (`LandingTrust`) | code (Apache-2.0), measured runs, docs — one row | each card |
| 6 | How do I start? | `LandingClosing` | — | Talk to us / Get started |

Moved off the home: the pillars (feature pages, footer), the platform list and the « three commands » (docs:
self-hosting), the model list (/models), the pack shelves by category (/docs/packs, where the packs session groups
them), the Mac band (the Mac tab and /desktop). Rationale: progressive disclosure — the home answers each question
once, briefly, and links to the page that goes deeper (Nielsen Norman Group, *Progressive Disclosure*, 2006);
a story arc in the visitor's order beats a catalogue (Krug, *Don't Make Me Think*, 2014).

Honest media: the Web steps are recorded on dev.sovkora.com with the QA account (`scripts/record-web-steps.mjs`), the
phone steps are the existing simulator recordings; the **Mac** shows its one real capture until a recording exists
(recording the app needs macOS Screen Recording permission on the machine that films it).

## 5. /desktop — Sovkora for Mac

One sentence (run it here, or connect it to your workspace) and the app in a macOS window; **one** Download with its
facts quiet and the SHA-256 in « Verify the download »; « First launch » in three steps (the build is not yet signed
by Apple) with the quarantine fix folded in a `bash` block; the two modes and the folder scope; the services « Start »
brings up (from the CLI's own start phases); troubleshooting (Run doctor, logs). Removed: a drawn « showcase » with
figures nothing measured (« 0.8 ms vs 19 ms », « 18.4 tok/s ») and jargon (« studio sagas », « Metal acceleration »).

## 6. Decisions

| Decision | Problem | Principle | Refused | Cost | Signal we are wrong |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Three entries grouped by question (Product · Platforms · Docs) | six mixed items, a visitor scanned all of them | Hick's law — choice time grows with the number of options (Hick 1952); card-sorting by user goals (Spencer, *Card Sorting*, 2009) | « Models & infra » as a fourth menu (splits *what it does* from *which models*) | Demo and Mobile one click deeper | Analytics or interviews: visitors open Product looking for Self-host, or never find the Demo |
| Compact menu, intent delay, one item lit | a sheet over the hero; menus flashing open on pass-over; two items lit | Recognition over recall, minimise visual noise (Nielsen, *10 Usability Heuristics*, 1994); hover intent (Amazon mega-menu pattern, Kalbach, *Designing Web Navigation*, 2007) | hover-only menus; a two-column mega-menu for nine links | descriptions must fit one line | people open menus by mistake, or miss that Product is a menu |
| One list of sections for rail, palette and phone | the same place under three names | Consistency and standards (Nielsen 1994); the same word everywhere builds one mental model (Norman, *The Design of Everyday Things*, 2013) | separate per-surface wording | Studios moves before Chat on the web rail (the phone's order) | users of both surfaces look for « Profile » or « Task History » |
| Six sections in the visitor's order | twelve sections, the same question answered three times | Progressive disclosure (Nielsen 2006); serial position — first and last are remembered (Ebbinghaus; Murdock 1962) | a long page « so everything is there » | Studios, packs and models one click deeper | scroll depth stops before « Where it runs », or visitors look for the models on the home |
| Account under the avatar, sections on the rail | Sign out on the rail, language as a third header control | Spatial memory — controls in conventional places (Fitts' law on corners; Jakob's law, Nielsen 2000) | a Settings entry in both the rail and the menu | the language is one click deeper in the app | people ask where to sign out or change the language |
| One Studio list with its state and one action per row, pack in a drawer | a 2-column grid of cards, three per screen; nobody could tell installed from available from « needs a pack » | Visibility of system status and recognition over recall (Nielsen 1994); information scent, each row says what you get and what it costs (Pirolli & Card, *Information Foraging*, 1999) | two tabs Ready / Get more (the same Studio in two places depending on what you own) | the cards' descriptions are one truncated line | people search for a Studio and open the wrong trade, or never open « Get more » |

*Owner: the UX lead session. Change a section here first, then in `site-nav.ts` / `app-sections.ts` and the phone's tab
bar, in the same pull requests.*
