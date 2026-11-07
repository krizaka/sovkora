# UX decisions — why Sovkora is built like this

Each notable choice of experience in Sovkora, on its three surfaces — the web app and public site, the operator console, the
phone: the decision, the problem, the principle behind it (with its source), what was refused, what it costs, and the signal
that would tell us we are wrong. Only choices that exist in the code are written here, each with the place that carries it and
the test that keeps its shape. The longer reasoning, with before/after of the menus, is
[`docs/ux/information-architecture.md`](ux/information-architecture.md). Disagree, or saw that signal? Say it in
[Discussion #182](https://github.com/krizaka/orazaka/discussions/182).

Dev preview criteria answered here (`dev-preview.json`): `ux.ia-review` (web), `admin.ux-review` (console),
`mobile.ux-review` (phone). A change to a menu starts here, then lands in the file named under *Carried by*, in the same pull
request as its test.

---

## Web — the public site and the signed-in app

### The public bar is three entries, grouped by the visitor's question

- **Decision** — Product ▾ (what does it do?) · Platforms ▾ (where does it run?) · Docs (how do I use it?); at most four
  entries a menu, none in two menus, no entry that stands alone while belonging to a group (no top-level Demo, Mobile or Mac).
- **Problem** — Six mixed items (Product, Demo, Mobile, Documentation, a version chip): a visitor scanned all of them to find
  where Sovkora runs.
- **Principle** — Choice time grows with the number of options (Hick, 1952; Hick–Hyman law); grouping by goals found by card
  sorting rather than by the team's org chart (Spencer, *Card Sorting*, 2009); working memory holds about four chunks (Cowan, 2001),
  which is why a menu stops at four entries.
- **Refused** — A fourth menu « Models & infra » (splits *what it does* from *which models*); hover-only menus.
- **Cost** — Demo and Mobile are one click deeper.
- **Signal we are wrong** — Visitors open Product looking for Self-host, or never find the Demo.
- **Carried by** — `orazaka-web-client/src/features/landing/utils/site-nav.ts`; shape test
  `src/features/landing/utils/__tests__/nav-shape.test.ts`, structure test `LandingNav.test.tsx`.

### One list of places for the rail, the palette and the phone

- **Decision** — Home · Studios · Chat · Jobs · Packs · Usage on the rail (six), Settings and Admin at its foot; the same list,
  words and order feed the left rail, the drawer on a phone browser and the ⌘K palette. Who you are (name, language, sign out)
  sits under the avatar, never also on the rail.
- **Problem** — The same place under three names (Chat Sessions / Chat, Task History / Jobs, Credits / Usage, Profile /
  Settings) and three lists that drifted apart.
- **Principle** — Consistency and standards (Nielsen, *10 Usability Heuristics*, 1994); one word per concept builds one mental
  model (Norman, *The Design of Everyday Things*, 2013); controls in conventional places (Jakob's law, Nielsen 2000).
- **Refused** — Per-surface wording; a Gallery section (the results of a run are in Jobs: one concept, one place).
- **Cost** — Six places on the rail where five is the target: Packs stays a place of its own because buying and managing
  packs is a different job from running a Studio. If analytics show Packs is opened less than once per user per week, it folds
  into Studios (the plan line already links to it).
- **Signal we are wrong** — Users of both surfaces look for « Profile » or « Task History »; Packs is almost never opened.
- **Carried by** — `src/core/config/app-sections.ts`; tests `Sidebar.test.tsx`, `nav-shape.test.ts` (six places at most, two at
  the foot, every address once, Admin never on the main rail).

### One Studio list with its state and one action per row; the plan on top

- **Decision** — `/studios` is one list grouped by trade: a line with the plan and its credits, search, a state filter (All ·
  Ready · Get more) and a trade filter kept in the address (`?q=`, `?show=`, `?trade=`, `?pack=`), one action per row (Run,
  Get pack), a pack in a drawer. `/packs` is the compact « Plan & packs ». No install step: a vertical Studio installs on its
  first run, only a Studio whose blueprint declares settings asks for them once.
- **Problem** — A two-column grid of cards, three per screen; nobody could tell installed from available from « needs a pack »
  from « not on your plan ».
- **Principle** — Visibility of system status and recognition over recall (Nielsen, 1994); information scent — each row says
  what you get and what it costs (Pirolli & Card, *Information Foraging*, 1999).
- **Refused** — Two tabs Ready / Get more (the same Studio in two places depending on what you own).
- **Cost** — The description is one truncated line per row.
- **Signal we are wrong** — People search for a Studio and open the wrong trade, or never open « Get more ».
- **Carried by** — `src/app/(protected)/studios/StudiosScreen.tsx` and `packs/PacksScreen.tsx`; e2e `studios-catalogue.spec.ts`.

### A wellbeing Studio asks for consent before its first run, in its own words

- **Decision** — A regulated Studio (Guided journaling, Mood tracking) answers its install with `451 consent_required`; the web
  shows the statement **verbatim** from the server, asks the age and the region, and an **unticked** checkbox; nothing starts until
  all three are given; consent can be withdrawn, and « Delete all my wellbeing data » is one action in the Studio's page.
- **Problem** — Health-adjacent text was processed on a first run with no explicit, informed consent.
- **Principle** — Consent must be explicit, informed and as easy to withdraw as to give (Québec's private-sector privacy Act as amended by Law 25, s. 14; GDPR art. 7);
  a pre-ticked box is not a choice (CJEU, *Planet49*, 2019); nothing is assumed by default (Johnson & Goldstein, *Science*, 2003).
- **Refused** — A pre-ticked box; a link to terms in the footer; showing the statement after the first run.
- **Cost** — One more screen on the first run of three Studios.
- **Signal we are wrong** — Users abandon at the consent step far more than at any other first-run step, or the statement is
  read as a wall of text.
- **Carried by** — `src/features/studio/consent/` (`ConsentDialog.tsx`, test `ConsentDialog.test.tsx`); e2e `wellbeing-consent.spec.ts`; server `orazaka-studio` (451).

---

## Console — admin.sovkora.com

### Five places, two of them tabbed

- **Decision** — The bar shows **Supervision · Operations · Accounts · Catalogue · Audit log**. Accounts holds People and Billing &
  credits; Catalogue holds Models and Studios; a group of two opens its screens as tabs under the bar (the kit's `Tabs`). Every
  address is unchanged.
- **Problem** — Seven peers in one row (Supervision, Operations, Models, People, Audit log, Billing, Studios): concepts mixed, no
  order of use.
- **Principle** — Choice time grows with the number of options (Hick, 1952); group by the operator's job, not by the service that
  serves it (card sorting by goals, Spencer, 2009); about five chunks is where scanning stays effortless (Cowan, 2001).
- **Refused** — A left rail of eleven items; one page per service; folding Audit into Operations (what is *done* is a different job
  from what is *running*, and an auditor opens only that one).
- **Cost** — Billing and Studios are one extra click for an operator who lives in them.
- **Signal we are wrong** — Operators open the tabs and go straight back (the group hid what they wanted), or ask for a shortcut to
  one screen.
- **Carried by** — `orazaka-web-client/src/console/core/config/sections.ts`, `ConsoleNav.tsx`, `ConsoleSubnav.tsx`; shape test
  `src/console/test/sections.test.mts` (five places at most, every route once, no mixed concepts).

### The console says where it is: a DEV badge, no family switcher, its own footer

- **Decision** — The bar carries the environment badge (DEV on dev, nothing in production) and never the family switcher or the
  public footer; the console has its own session, key and sign-in flag, and is reachable only from the allowed addresses.
- **Problem** — An operator acting on the wrong environment, or a console that looks like the public site.
- **Principle** — Visibility of system status (Nielsen, 1994); a dangerous place looks different from a safe one (Norman, 2013).
- **Refused** — A colour-only cue (invisible to some users); the family switcher on a private tool.
- **Cost** — None known.
- **Signal we are wrong** — An operator reports an action on the wrong environment.
- **Carried by** — `ConsoleNav.tsx`; test `src/console/test/nav-family.test.mts`.

---

## Phone — the Sovkora app

### Four tabs at the thumb, Studios first

- **Decision** — Studios · Chat · Jobs · Settings, in a tab bar at the bottom edge, each an `@krizaka/icons` glyph **and** a word that
  is never truncated. The app opens on Studios.
- **Problem** — On a phone the rail is a drawer behind a menu button at the top: the most-used places are the hardest to reach, one
  handed.
- **Principle** — Most people hold a phone in one hand and reach the lower half of the screen easily (Hoober, *How Do Users Really
  Hold Mobile Devices?*, 2013); a bar of three to five items beats a hidden drawer for discoverability (Nielsen Norman Group, *Hamburger
  Menus and Hidden Navigation Hurt UX Metrics*, 2016); an icon alone is ambiguous, an icon with its label is not (NN/g, *Icon
  Usability*, 2014).
- **Refused** — A drawer; seven tabs (the web rail's Home, Packs and Usage are bought, planned and read on the web: the phone keeps
  the four that are about *doing*); icon-only tabs.
- **Cost** — Plan, packs and credits are not on the phone: a person who wants them goes to the web.
- **Signal we are wrong** — Support asks where to buy a pack from the phone, or Settings is opened mostly to reach the plan.
- **Carried by** — `orazaka-mobile-client/src/navigation/AppTabBar.tsx`; test `src/__tests__/nav-shape.test.tsx` (≤ 5 tabs, Studios
  first, the web's words in the web's order).

### The same journey as the web: Studio → form → run → result, in the same words

- **Decision** — A Studio's page, its input form (an enum is a row of chips, a number is a number), the run with each step's state, then
  the outputs **as the blueprint typed them**: a picture inline, a clip or voice played, a rules report as cards (failures first, the
  passage quoted, the article cited), a model's text as Markdown. Sign-in keeps the session in the device's secure storage only.
- **Problem** — A phone app that shows raw JSON or a different vocabulary from the web makes the same product feel like two.
- **Principle** — Match between the system and the real world (Nielsen, 1994); one mental model across devices (Norman, 2013).
- **Refused** — A web view of the web client (cookies, keyboard, autofill break); a simplified run form with rules of its own.
- **Cost** — Two clients to keep in step (the server holds the rules; both draw its schemas and refusals).
- **Signal we are wrong** — A run started on the phone cannot be found in Jobs on the web, or a refusal reads differently.
- **Carried by** — `src/screens/StudioDetailScreen.tsx`, `StudioRunScreen.tsx`, `ArtefactCard.tsx`; render tests `src/__tests__/app.test.tsx`
  and `app.fr.test.tsx`; the walk is recorded on a simulator and an emulator (docs/MOBILE.md).

---

*Owner: the UX session. Change a section here first, then in `site-nav.ts` / `app-sections.ts` / `sections.ts` and the phone's tab bar,
in the same pull requests, with the shape tests above.*
