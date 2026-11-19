//! A granted repository folder and the path resolution every code tool goes through (ADR-093 §4.5).

use super::visibility;
use krizaka_rust::scope::ScopeStore;
use serde::Serialize;
use std::path::{Component, Path, PathBuf};
use std::process::Command;

#[derive(Debug, PartialEq, Eq)]
pub enum CodeError {
    /// Not a git repository, or not grantable.
    NotARepository(String),
    /// The path leaves the repository (`..`, absolute elsewhere, symlink out).
    OutsideRepository(String),
    /// `.git/**`, a secret pattern, a symlink target or a hard-linked file: never written.
    Refused(String),
    /// Invisible to every read tool (gitignored, secret pattern, `.git/`).
    Invisible(String),
    NotFound(String),
    /// The file changed since the diff was proposed: refuse and re-propose.
    Conflict(String),
    Io(String),
}

impl std::fmt::Display for CodeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            CodeError::NotARepository(p) => write!(f, "not a git repository: {p}"),
            CodeError::OutsideRepository(p) => write!(f, "outside the repository: {p}"),
            CodeError::Refused(p) => write!(f, "refused target: {p}"),
            CodeError::Invisible(p) => write!(f, "not visible: {p}"),
            CodeError::NotFound(p) => write!(f, "not found: {p}"),
            CodeError::Conflict(p) => {
                write!(f, "the file changed since the proposal, propose again: {p}")
            }
            CodeError::Io(e) => write!(f, "io: {e}"),
        }
    }
}

impl std::error::Error for CodeError {}

/// What the « Code » tab shows once a folder is granted.
#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct RepoInfo {
    pub root: String,
    pub branch: String,
    /// `git status --short` lines (secret paths are not listed).
    pub status: Vec<String>,
    /// How many files Sovkora can see, and how many it cannot (ignored, secret).
    pub visible: usize,
    pub hidden: usize,
}

#[derive(Debug, Clone)]
pub struct Repo {
    root: PathBuf,
}

impl Repo {
    /// Grants `folder` through the kit's scope rules (too broad or sensitive folders are refused) and requires git.
    pub fn open(folder: &Path, home: Option<&Path>) -> Result<Repo, CodeError> {
        let mut store = ScopeStore::default();
        let root = store
            .grant_with_home(folder, home)
            .map_err(|e| CodeError::NotARepository(e.to_string()))?;
        if !root.join(".git").exists() {
            return Err(CodeError::NotARepository(root.display().to_string()));
        }
        Ok(Repo { root })
    }

    pub fn root(&self) -> &Path {
        &self.root
    }

    pub(crate) fn git(&self, args: &[&str]) -> Result<Vec<u8>, CodeError> {
        let out = Command::new("git")
            .arg("-C")
            .arg(&self.root)
            .args(args)
            .env("GIT_TERMINAL_PROMPT", "0")
            .output()
            .map_err(|e| CodeError::Io(e.to_string()))?;
        if out.status.success() {
            Ok(out.stdout)
        } else {
            Err(CodeError::Io(
                String::from_utf8_lossy(&out.stderr).trim().to_string(),
            ))
        }
    }

    pub fn info(&self) -> Result<RepoInfo, CodeError> {
        let branch = String::from_utf8_lossy(
            &self
                .git(&["rev-parse", "--abbrev-ref", "HEAD"])
                .unwrap_or_default(),
        )
        .trim()
        .to_string();
        let status = String::from_utf8_lossy(&self.git(&["status", "--short"])?)
            .lines()
            .filter(|l| {
                l.len() > 3 && visibility::is_secret_path(l[3..].trim_matches('"')).is_none()
            })
            .map(str::to_string)
            .collect();
        let all = self.candidate_files()?;
        let visible = visibility::filter_visible(self, &all).len();
        Ok(RepoInfo {
            root: self.root.display().to_string(),
            branch,
            status,
            visible,
            hidden: all.len() - visible,
        })
    }

    /// Tracked and untracked files (before the visibility filter), relative to the root.
    pub(crate) fn candidate_files(&self) -> Result<Vec<String>, CodeError> {
        let out = self.git(&["ls-files", "-co", "-z"])?;
        Ok(out
            .split(|b| *b == 0)
            .filter(|s| !s.is_empty())
            .map(|s| String::from_utf8_lossy(s).to_string())
            .collect())
    }

    /// A relative path made only of normal components (no `..`, no root): the lexical gate.
    fn lexical(&self, rel: &str) -> Result<PathBuf, CodeError> {
        let p = Path::new(rel);
        let p = if p.is_absolute() {
            p.strip_prefix(&self.root)
                .map_err(|_| CodeError::OutsideRepository(rel.to_string()))?
                .to_path_buf()
        } else {
            p.to_path_buf()
        };
        let mut clean = PathBuf::new();
        for c in p.components() {
            match c {
                Component::Normal(n) => clean.push(n),
                Component::CurDir => {}
                _ => return Err(CodeError::OutsideRepository(rel.to_string())),
            }
        }
        Ok(clean)
    }

    /// Resolves a path for **reading**: canonical, inside the repository, visible (by the given name and by its target).
    pub fn resolve_read(&self, rel: &str) -> Result<PathBuf, CodeError> {
        let clean = self.lexical(rel)?;
        let rel_s = clean.to_string_lossy().to_string();
        let canon = self
            .root
            .join(&clean)
            .canonicalize()
            .map_err(|_| CodeError::NotFound(rel.to_string()))?;
        let inside = canon
            .strip_prefix(&self.root)
            .map_err(|_| CodeError::OutsideRepository(rel.to_string()))?;
        let target_rel = inside.to_string_lossy().to_string();
        for r in [&rel_s, &target_rel] {
            if !r.is_empty() && !visibility::is_visible(self, r) {
                return Err(CodeError::Invisible(rel.to_string()));
            }
        }
        Ok(canon)
    }

    /// Resolves a path for **writing**. Run at the call and again right before the write. Refuses `.git/**`, secret
    /// patterns, a symlink target, a hard-linked file, and any path whose existing ancestors leave the repository.
    pub fn resolve_write(&self, rel: &str) -> Result<PathBuf, CodeError> {
        let clean = self.lexical(rel)?;
        if clean.as_os_str().is_empty() {
            return Err(CodeError::Refused(rel.to_string()));
        }
        let rel_s = clean.to_string_lossy().to_string();
        if visibility::is_git_internal(&rel_s) || visibility::is_secret_path(&rel_s).is_some() {
            return Err(CodeError::Refused(rel.to_string()));
        }
        let target = self.root.join(&clean);
        // The deepest existing ancestor is canonical and inside (a new file's parent is resolved before it is created).
        let mut anc = target
            .parent()
            .map(Path::to_path_buf)
            .unwrap_or_else(|| self.root.clone());
        while !anc.exists() {
            if anc.symlink_metadata().is_ok() {
                return Err(CodeError::OutsideRepository(rel.to_string())); // a dangling symlink in the chain
            }
            anc = anc
                .parent()
                .ok_or_else(|| CodeError::OutsideRepository(rel.to_string()))?
                .to_path_buf();
        }
        let canon_anc = anc
            .canonicalize()
            .map_err(|e| CodeError::Io(e.to_string()))?;
        let tail = canon_anc
            .strip_prefix(&self.root)
            .map_err(|_| CodeError::OutsideRepository(rel.to_string()))?;
        // `.git` or a secret directory reached through a symlinked directory is refused too.
        let full = tail
            .join(clean.file_name().unwrap_or_default())
            .to_string_lossy()
            .to_string();
        if visibility::is_git_internal(&full) || visibility::is_secret_path(&full).is_some() {
            return Err(CodeError::Refused(rel.to_string()));
        }
        if let Ok(md) = target.symlink_metadata() {
            if md.file_type().is_symlink() {
                return Err(CodeError::Refused(rel.to_string()));
            }
            #[cfg(unix)]
            {
                use std::os::unix::fs::MetadataExt;
                if md.is_file() && md.nlink() > 1 {
                    return Err(CodeError::Refused(rel.to_string()));
                }
            }
        }
        Ok(target)
    }
}
