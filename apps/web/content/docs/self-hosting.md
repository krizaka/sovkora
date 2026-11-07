# Install, update and back up

Orazaka runs on hardware you own. Your documents, prompts and results stay there, nothing is sent to Krizaka, and once the models are downloaded it works offline.

> **Where this stands.** The packaged installers (Orazaka Desktop for Mac, the one-command server install) arrive with **1.0** ([delivery epic](https://github.com/krizaka/orazaka/issues/75)). Commands marked **1.0** are not available yet. The [developer install](#today-the-developer-install) runs the same platform from source today.

## Requirements

|         | Mac                                    | Linux server                                                                            |
| ------- | -------------------------------------- | --------------------------------------------------------------------------------------- |
| Machine | Apple Silicon (M1 or later), macOS 14+ | x86-64 or arm64; Ubuntu 24.04, Debian 12 or RHEL 9                                      |
| Memory  | 16 GB minimum, 32 GB recommended       | 32 GB recommended                                                                       |
| Disk    | 150 GB free                            | 150 GB free; about 35 GB more per video model                                           |
| GPU     | the Mac's own                          | optional NVIDIA, 20 GB VRAM or more; without one, text, documents and small images only |
| Also    | FileVault on                           | Docker Engine 27+ with compose                                                          |

## On a Mac (in testing, not distributed yet)

The Mac app is an unsigned test build, still in testing: it is not distributed, so there is no download yet. Until it is, run `npx orazaka install`.

1. Run `npx orazaka install`. (When the Mac app is distributed, you will download it and drag it into Applications.)
2. Open it. It checks your Mac, downloads the models once, and starts Orazaka.
3. Open `http://localhost:3000` and create the administrator account. Sign-up stays closed: you invite your colleagues.

## On a Linux server (1.0)

1. Run `curl -fsSL https://www.orazaka.com/install.sh | sh` (or `npx orazaka install --server`). It checks the server, verifies every image signature, writes every secret to `/etc/orazaka`, and starts a systemd service.
2. Open `https://<server>` from your network.
3. Create the administrator account.

No Internet on the server? Use the offline kit: `sudo ./orazaka install --server`.

## Living with it (1.0)

| Task           | Command                                                              |
| -------------- | -------------------------------------------------------------------- |
| Check health   | `orazaka doctor`                                                     |
| Update         | `orazaka update`: verifies the signature and backs up first          |
| Undo an update | `orazaka rollback`                                                   |
| Back up        | `orazaka backup`: nightly by default; keep the key apart             |
| Restore        | `orazaka restore <archive>`                                          |
| Get help       | `orazaka doctor --bundle`: a support file that stays on your machine |
| Remove         | `orazaka uninstall`; `--purge` also deletes data and models          |

## Today: the developer install

Available now, from source. It needs git, JDK 21, Node.js 22+, Docker and Python 3.11+.

```bash
npx orazaka install
cd orazaka && ./mvnw install && (cd orazaka-apps/ui && npm install)
npx orazaka start    # PostgreSQL, Redis, RabbitMQ, local AI engines
npx orazaka dev      # the services and the web app on port 3000
```

`npx orazaka stop` stops everything; `--purge` also removes the data.

## Next

- [Quickstart](101.md): first chat and first Studio.
- [Security policy](https://github.com/krizaka/orazaka/blob/main/SECURITY.md): reporting a vulnerability.
