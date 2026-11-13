//! App settings: connect mode, the remote Sovkora, the workspace folder, watched folders.

use krizaka_rust::files::Limits;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

pub const DEFAULT_REMOTE: &str = "https://www.orazaka.com";
pub const LOCAL_WORKSPACE_URL: &str = "http://localhost:3000";

// @fixed: a megabyte and a kilobyte, units of the limits below, not settings
const KB: u64 = 1024;
const MB: u64 = 1024 * KB;

/// What the files panel may list, preview and send, in the units a person reads. A limit left out is the kit's default,
/// and a value above the kit's ceiling is lowered by the kit, never obeyed (`krizaka_rust::files::Limits`).
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct FileLimits {
    pub max_entries: Option<usize>,
    pub max_text_preview_kb: Option<u64>,
    pub max_image_preview_mb: Option<u64>,
    pub max_pdf_preview_mb: Option<u64>,
    pub max_send_mb: Option<u64>,
}

impl FileLimits {
    /// The limits the app passes to the kit's `list_dir` and `preview`.
    pub fn to_kit(&self) -> Limits {
        let bytes = |value: Option<u64>, unit: u64| value.map(|v| v.saturating_mul(unit));
        Limits {
            max_entries: self.max_entries,
            max_text_preview: bytes(self.max_text_preview_kb, KB)
                .map(|b| usize::try_from(b).unwrap_or(usize::MAX)),
            max_image_preview: bytes(self.max_image_preview_mb, MB),
            max_pdf_preview: bytes(self.max_pdf_preview_mb, MB),
            max_send: bytes(self.max_send_mb, MB),
        }
    }
}

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
    /// What the files panel lists, previews and sends; a file saved before this field existed reads the kit's defaults.
    #[serde(default)]
    pub limits: FileLimits,
}

impl Default for Settings {
    fn default() -> Self {
        Settings {
            mode: "remote".into(),
            remote_url: DEFAULT_REMOTE.into(),
            workspace_dir: None,
            watched: vec![],
            locale: "en".into(),
            limits: FileLimits::default(),
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
    krizaka_rust::nav_guard::normalize_origin(input).map_err(|e| e.to_string())
}

/// True when `target` is on the same origin as `allowed` — the navigation guard of the workspace window.
pub use krizaka_rust::nav_guard::same_origin;

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

    #[test]
    fn a_settings_file_from_before_the_limits_reads_the_kit_defaults(
    ) -> Result<(), serde_json::Error> {
        let old = r#"{"mode":"remote","remoteUrl":"https://sovkora.example","workspaceDir":null,"watched":[],"locale":"en"}"#;
        let s: Settings = serde_json::from_str(old)?;
        assert_eq!(s.limits, FileLimits::default());
        assert_eq!(s.limits.to_kit(), Limits::default());
        assert_eq!(Limits::default().pdf_preview(), 12 * MB);
        assert_eq!(Limits::default().send(), 50 * MB);
        Ok(())
    }

    #[test]
    fn the_limits_are_read_in_megabytes_and_the_kit_lowers_what_exceeds_its_ceiling(
    ) -> Result<(), serde_json::Error> {
        let json = r#"{"maxPdfPreviewMb":20,"maxSendMb":100,"maxTextPreviewKb":8,"maxEntries":50}"#;
        let limits: FileLimits = serde_json::from_str(json)?;
        let kit = limits.to_kit();
        assert_eq!(kit.pdf_preview(), 20 * MB);
        assert_eq!(kit.send(), 100 * MB);
        assert_eq!(kit.text_preview(), 8 * 1024);
        assert_eq!(kit.entries(), 50);
        let absurd = FileLimits {
            max_send_mb: Some(u64::MAX),
            max_pdf_preview_mb: Some(1_000_000),
            ..Default::default()
        }
        .to_kit();
        assert_eq!(absurd.send(), 200 * MB);
        assert_eq!(absurd.pdf_preview(), 50 * MB);
        Ok(())
    }
}
