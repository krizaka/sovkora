//! App settings: connect mode, the remote Sovkora, the workspace folder, watched folders.

use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

pub const DEFAULT_REMOTE: &str = "https://www.orazaka.com";
pub const LOCAL_WORKSPACE_URL: &str = "http://localhost:3000";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Settings {
    /// "local" (the stack started by the CLI) or "remote" (a hosted Sovkora).
    pub mode: String,
    pub remote_url: String,
    /// The Sovkora workspace the CLI runs in (where `modules.json` lives).
    pub workspace_dir: Option<String>,
    pub watched: Vec<PathBuf>,
    pub locale: String,
}

impl Default for Settings {
    fn default() -> Self {
        Settings {
            mode: "remote".into(),
            remote_url: DEFAULT_REMOTE.into(),
            workspace_dir: None,
            watched: vec![],
            locale: "en".into(),
        }
    }
}

impl Settings {
    pub fn load(file: &Path) -> Self {
        fs::read_to_string(file)
            .ok()
            .and_then(|r| serde_json::from_str(&r).ok())
            .unwrap_or_default()
    }

    pub fn save(&self, file: &Path) -> Result<(), String> {
        if let Some(dir) = file.parent() {
            fs::create_dir_all(dir).map_err(|e| e.to_string())?;
        }
        fs::write(
            file,
            serde_json::to_string_pretty(self).map_err(|e| e.to_string())?,
        )
        .map_err(|e| e.to_string())
    }

    /// Checks everything that comes from the UI before it is stored.
    pub fn validated(mut self) -> Result<Self, String> {
        if self.mode != "local" && self.mode != "remote" {
            return Err("mode must be local or remote".into());
        }
        self.remote_url = normalize_remote(&self.remote_url)?;
        if self.locale != "en" && self.locale != "fr" {
            self.locale = "en".into();
        }
        if let Some(dir) = &self.workspace_dir {
            if dir.trim().is_empty() {
                self.workspace_dir = None;
            } else if !Path::new(dir).is_dir() {
                return Err(format!("workspace folder not found: {dir}"));
            }
        }
        Ok(self)
    }

    /// The URL "Open workspace" loads in the app window.
    pub fn workspace_url(&self) -> String {
        if self.mode == "local" {
            LOCAL_WORKSPACE_URL.into()
        } else {
            self.remote_url.clone()
        }
    }
}

/// A remote Sovkora must be HTTPS (plain http only for a loopback address), without credentials in the URL, and is
/// reduced to its origin: the app window may never leave it. The rule is krizaka-tauri-kit's navigation guard.
pub fn normalize_remote(input: &str) -> Result<String, String> {
    krizaka_tauri_kit::nav_guard::normalize_origin(input)
}

/// True when `target` is on the same origin as `allowed` — the navigation guard of the workspace window.
pub use krizaka_tauri_kit::nav_guard::same_origin;

#[cfg(test)]
mod tests {
    use super::*;
    use url::Url;

    #[test]
    fn remote_must_be_https_or_loopback_and_is_reduced_to_its_origin() {
        assert_eq!(
            normalize_remote("https://www.orazaka.com/some/path?x=1").unwrap(),
            "https://www.orazaka.com"
        );
        assert_eq!(
            normalize_remote("http://localhost:3000/").unwrap(),
            "http://localhost:3000"
        );
        assert!(normalize_remote("http://www.orazaka.com").is_err());
        assert!(normalize_remote("https://user:pw@www.orazaka.com").is_err());
        assert!(normalize_remote("file:///etc/passwd").is_err());
        assert!(normalize_remote("javascript:alert(1)").is_err());
        assert!(normalize_remote("nonsense").is_err());
    }

    #[test]
    fn navigation_guard_compares_origins() {
        let ok = Url::parse("https://www.orazaka.com/login").unwrap();
        let other = Url::parse("https://evil.example/").unwrap();
        let lookalike = Url::parse("https://www.orazaka.com.evil.example/").unwrap();
        assert!(same_origin("https://www.orazaka.com", &ok));
        assert!(!same_origin("https://www.orazaka.com", &other));
        assert!(!same_origin("https://www.orazaka.com", &lookalike));
    }

    #[test]
    fn settings_round_trip_and_validate() {
        let d = tempfile::tempdir().unwrap();
        let f = d.path().join("settings.json");
        let s = Settings {
            mode: "local".into(),
            ..Default::default()
        };
        s.save(&f).unwrap();
        assert_eq!(Settings::load(&f).mode, "local");
        let bad = Settings {
            mode: "root".into(),
            ..Default::default()
        };
        assert!(bad.validated().is_err());
        assert_eq!(
            Settings::default().validated().unwrap().workspace_url(),
            DEFAULT_REMOTE
        );
        let loc = Settings {
            mode: "local".into(),
            ..Default::default()
        };
        assert_eq!(loc.workspace_url(), LOCAL_WORKSPACE_URL);
    }
}
