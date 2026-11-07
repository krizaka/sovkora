# Security policy — Orazaka

**Please do not report vulnerabilities in public issues.**

## Reporting

Use GitHub's private vulnerability reporting: open the affected repository of the Orazaka platform
(this workspace, or the component — `krizaka/orazaka-cli`, `krizaka/orazaka-edge`, …), then
**Security → Report a vulnerability**. If that is not possible, write to us through the
[Krizaka contact form](https://www.krizaka.com/en/contact) without technical details, and we will open a
private channel.

We acknowledge a report within **two business days**, keep you informed while we fix it, publish a
GitHub security advisory with the fixed release, and credit you unless you prefer otherwise.

## Supported versions

| Version                                                       | Supported                                       |
| ------------------------------------------------------------- | ----------------------------------------------- |
| The latest Orazaka release (one version for the whole bundle) | yes                                             |
| The previous minor release                                    | security fixes for 90 days after the next minor |
| `main`                                                        | yes (development)                               |
| Anything older                                                | no — update with `orazaka update`               |

## In scope

The platform's services, workers, web and admin apps, the `orazaka` CLI, Orazaka Desktop, the release
artefacts (images on `ghcr.io/krizaka/*`, release manifests, installers) and the reference packs.
Third-party software we ship (PostgreSQL, Ollama, stable-diffusion.cpp…) is reported upstream; tell us
too if it affects Orazaka's defaults.

## What a self-hosted installation provides (from the first release)

- No default password: every secret is generated at install into a local file readable by the service
  user only (mode 600).
- Only the web entry point (the edge) listens beyond localhost; remote access goes through TLS.
- Containers run as non-root, with a read-only filesystem where the service allows it, from minimal
  images that are scanned in CI (a critical finding with a fix blocks a release).
- Images are signed (cosign keyless) with SBOM and SLSA provenance, referenced by digest, and verified
  before they run; release manifests are signed and verified offline.
- No telemetry: Orazaka sends nothing to Krizaka.

These defaults are under construction in the delivery epic ([#75](https://github.com/krizaka/orazaka/issues/75));
`orazaka doctor` checks them on a running installation.
