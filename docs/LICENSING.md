---
title: "Licensing — where the line falls in the tree"
description: "This repository is FSL-1.1-ALv2 from the licence commit (Apache-2.0 before it, and for each version two years after its release). What is proprietary lives in another repository, and the boundary is the repository, not a flag."
category: Reference
order: 2
---

# Licensing

**From the commit that introduces its `NOTICE`, this repository is licensed under FSL-1.1-ALv2**
(Functional Source License, Apache-2.0 future licence — [`LICENSE`](../LICENSE)): you may read, run, modify and
self-host it for any purpose except a Competing Use (offering it to others as a commercial service that substitutes for
Sovkora), and each version becomes Apache-2.0 two years after its release. Every earlier commit remains Apache-2.0
([`LICENSE-APACHE`](../LICENSE-APACHE)). The organisation decision is
ADR-012;
it updates ADR-049 on the licence of the engine and keeps its boundary between public and private repositories.

The pieces an integrator needs stay Apache-2.0 in their own repositories: `orazaka-packs` (the pack format and reference
packs), `orazaka-contracts`, `orazaka-cli`, `orazaka-ui-kit`, `orazaka-build`, `orazaka-edge`.

## Where the line falls

| Layer | Home | Licence |
|:---|:---|:---|
| Engine — studio, job plane, interceptors, persistence | **this repository** and the engine repositories | FSL-1.1-ALv2 (Apache-2.0 after two years) |
| Pack SPI — `pack.schema.json`, `orazaka-jobs-api`, `docs/WORKER_PROTOCOL.md`, `PackInstallerService`, the `orazaka pack` CLI | `orazaka-contracts`, `orazaka-cli`, `orazaka-packs` | Apache-2.0 |
| Reference packs — `orazaka-packs/**` | `orazaka-packs` | Apache-2.0 |
| Commercial packs | `orazaka-packs-cloud` (private) | proprietary |
| Hosted registry, managed workers, control plane, SLA | private | proprietary |

**The boundary is the repository, not a flag.** A `distribution: CLOUD` pack sitting in a public
repository is readable by everyone, which defeats the point; the manifest's `distribution` field
drives *catalogue visibility per deployment*, never secrecy (ADR-037 §4.1).

The two worlds differ by **one line of configuration** and nothing else:

```bash
# OSS deployment — the default
ORAZAKA_PACKS_SOURCES=orazaka-packs

# Cloud deployment — same artifact, one more source
ORAZAKA_PACKS_SOURCES=orazaka-packs,/srv/packs,registry:https://registry.orazaka.dev
```

If a cloud pack ever requires a change inside this repository, the split has failed and that is a
bug in the SPI, not a packaging decision. Phase G found four such changes and fixed them; they are
listed in ADR-049.

## Two things a reader mistakes

**`orazaka-packs/pack.schema.json` is SPI, not a pack.** It is the manifest contract the platform
enforces, and it happens to live beside the reference packs it describes. It is Apache-2.0 either
way, so nothing turns on the placement — but it is the platform's file, and the CLI resolves it
from its own installation rather than from whatever tree a bundle sits in.

**A reference pack is not a sample.** `orazaka-packs/echo-toolkit` is a Tier-W pack with a worker
that runs; the three others are Tier-D packs the product actually ships. They are reference in the
sense that they are the worked example of each tier, not in the sense of being throwaway.

## Third-party pack authors

A pack you write is yours. Nothing in Apache-2.0 obliges you to publish it, and the platform never
reads your bundle from this repository — see [CONTRIBUTING-PACKS.md](../CONTRIBUTING-PACKS.md).
