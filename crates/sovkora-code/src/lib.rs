//! Sovkora's code agent core (ADR-093), free of Tauri so that the Mac app and a headless runner share it
//! (krizaka-internal#4344). The Mac app's commands are the glue; every rule is here.
//!
//! * `scope`      — a granted repository; every path is resolved here (canonical, inside, no symlink out).
//! * `visibility` — the one function every read goes through, and the redactor.
//! * `tools`      — the read tools the agent will get: `read_file`, `list_dir`, `search_text`, `outline`.
//! * `diff`       — pending diffs, approved or rejected per hunk, applied exactly against the base hash.
//! * `snapshot`   — `refs/orazaka/snapshots/<run-id>` and the undo.
//! * `checks`     — slice 2: user-confirmed argv commands (the only `exec`), run in a Seatbelt sandbox.
//! * `editor`     — « Open in VS Code / Xcode » at a line.
//!
//! * `data`       — slice 3: the data channel (untrusted text, fenced, capped, redacted).
//! * `agent`      — slice 3: the closed tool list and the loop; the engine owns each model turn.
//!
//! * `secret`    — a credential that never prints, logs or serialises.
//! * `github`    — slice 4: the GitHub App connection (device flow, Keychain tokens, refresh, sign-out).

pub mod agent;
pub mod checks;
pub mod data;
pub mod diff;
pub mod editor;
pub mod github;
pub mod rules;
pub mod scope;
pub mod secret;
pub mod snapshot;
pub mod tools;
pub mod visibility;

pub use scope::{CodeError, Repo, RepoInfo};

#[cfg(test)]
mod tests;

#[cfg(test)]
mod tests_checks;

#[cfg(test)]
mod tests_agent;

#[cfg(test)]
mod tests_wire;
