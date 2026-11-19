# sovkora-code

The code agent core of Sovkora (ADR-093), as a Rust library with no Tauri dependency, so that the Mac app
(`apps/desktop`) and a headless runner share one set of rules (krizaka-internal#4344).

| Module | What it holds |
| :--- | :--- |
| `scope` | `Repo`: the granted repository; every path is resolved inside it (canonical, no `..`, no symlink out). |
| `visibility` | The one filter every read goes through (no `.git/**`, no secret paths, no gitignored file; an error of the gitignore check hides everything) and the redactor of token shapes. |
| `tools` | The read tools: `read_file`, `list_dir`, `search_text`, `outline`. |
| `data` | The fenced data channel for untrusted text. |
| `diff` | Pending diffs approved per hunk and applied exactly against their base hash. |
| `snapshot` | The undo refs `refs/orazaka/snapshots/<run-id>`. |
| `checks` | The confirmed checks, run in a Seatbelt sandbox on macOS (refused without it). |
| `rules` | Budgets and limits as data, bounded by code ceilings. |
| `agent` | The closed tool list, the loop, the `Engine` port and `SessionEngine` (https only, plain http to loopback only, no redirect). |
| `secret` | `Secret`: a credential with no `Display`, `Debug`, `Serialize` or `Clone`. |
| `github` | The device flow of the GitHub App and its token vault over any `SecretStore`. |
| `editor` | « Open in VS Code / Xcode » at a line (macOS). |

The crate must stay free of Tauri: `crates/deny.toml` bans the crate, and no file of this folder names it in lower case.

## Run it

From `crates/`:

```sh
cargo fmt --all --check
cargo clippy --all-targets -- -D warnings
cargo test
```

The tests make throwaway git repositories under the system temporary folder. CI runs them on macOS
(`lang-guards.yml`, job `rust-crates`) so the Seatbelt tests run instead of skipping.
