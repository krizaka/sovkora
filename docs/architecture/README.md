# Sovkora's architecture, as code

ADR-027 (krizaka/.github) makes each
system's C1 and C2, current and target, a model in its home repository. This folder is Sovkora's.

## Written by hand, here

- [`sovkora.c4`](sovkora.c4): the inside of the `sovkora` system, in [LikeC4](https://likec4.dev). Each container says what
  it is for, its technology and, when it is code, the folder of the one repository it is (ADR-029):
  `metadata { repo 'sovkora'\n path 'services/edge' }`.
  The relationships say how the parts talk.
- The **target**: an element or a relationship tagged `#planned` exists only in the target, one tagged `#retiring` only in
  the current state. Each carries the ADR (`adr 'sovkora ADR-087'`) or the issue (`issue 'krizaka-internal#2136'`) that
  decides it. An untagged element is current and target.

## Generated, never written here

- The `sovkora` system itself, its name and links (from `@krizaka/org`), the specification of the element kinds and tags
  (`krizaka/.github` `docs/architecture/specification.c4`), and the standard views `sovkora_c1` and `sovkora_c2`.
- The facts read from `main`: the units of every Sovkora folder, their versions, their latest tags and the Krizaka
  packages each pins. They are never typed in a model.

## Checked against `main`

The architecture tool of `krizaka/.github` compares this model with the facts: a repository on `main` that no current
element binds (`undocumented`), an element bound to something `main` does not have (`phantom`), a planned element already
on `main` (`reached`), a retiring one already gone (`gone`). The conformance rule `architecture-model` counts them; the
founder's cockpit draws the views.

Check a change before its pull request (the tool reads tracked and staged files, so `git add` first):

```sh
node <krizaka/.github checkout>/tools/architecture/architecture.mjs --local "$(~/.local/bin/kz-config repos)" \
  --use sovkora=<this checkout> --repo sovkora
```
