//! Proposed diffs (ADR-093 §5.5): a diff is a pending object with the base hash of its file, never a write.
//! Each hunk is approved or rejected on its own; application is exact, and refused when the base changed.

use super::scope::{CodeError, Repo};
use super::visibility::has_secret;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use similar::{DiffOp, TextDiff};
use std::fs;
use std::path::Path;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Hunk {
    pub id: usize,
    /// 0-based index of the first replaced line of the base.
    pub old_start: usize,
    /// Base lines replaced (with their line terminators), and the lines that replace them.
    pub old_lines: Vec<String>,
    pub new_lines: Vec<String>,
    /// « Changes what runs »: touches a script, a hook, a workflow or a test config (ADR-093 §4.4).
    pub exec_surface: bool,
    /// The new lines hold a token-shaped string.
    pub adds_secret: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct FileProposal {
    pub path: String,
    /// SHA-256 of the file as proposed against; `None` for a new file.
    pub base_hash: Option<String>,
    pub hunks: Vec<Hunk>,
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Applied {
    pub path: String,
    pub written: bool,
    pub hunks_applied: usize,
}

pub fn hash_bytes(b: &[u8]) -> String {
    Sha256::digest(b)
        .iter()
        .map(|x| format!("{x:02x}"))
        .collect()
}

fn lines_of(s: &str) -> Vec<String> {
    s.split_inclusive('\n').map(str::to_string).collect()
}

/// Proposes `new_text` for `rel`: computes the hunks against the file as it is now. Writes nothing.
pub fn propose(repo: &Repo, rel: &str, new_text: &str) -> Result<FileProposal, CodeError> {
    let target = repo.resolve_write(rel)?;
    let (base_hash, base) = match fs::read(&target) {
        Ok(bytes) => {
            let text = String::from_utf8(bytes.clone())
                .map_err(|_| CodeError::Refused(format!("{rel} is not text")))?;
            (Some(hash_bytes(&bytes)), text)
        }
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => (None, String::new()),
        Err(e) => return Err(CodeError::Io(e.to_string())),
    };
    let base_lines = lines_of(&base);
    let new_all = lines_of(new_text);
    let (old_refs, new_refs): (Vec<&str>, Vec<&str>) = (
        base_lines.iter().map(String::as_str).collect(),
        new_all.iter().map(String::as_str).collect(),
    );
    let diff = TextDiff::from_slices(&old_refs, &new_refs);
    let mut hunks = Vec::new();
    for op in diff.ops() {
        let (old_start, old_len, new_start, new_len) = match *op {
            DiffOp::Equal { .. } => continue,
            DiffOp::Delete {
                old_index,
                old_len,
                new_index,
            } => (old_index, old_len, new_index, 0),
            DiffOp::Insert {
                old_index,
                new_index,
                new_len,
            } => (old_index, 0, new_index, new_len),
            DiffOp::Replace {
                old_index,
                old_len,
                new_index,
                new_len,
            } => (old_index, old_len, new_index, new_len),
        };
        let new_lines: Vec<String> = new_all[new_start..new_start + new_len].to_vec();
        hunks.push(Hunk {
            id: hunks.len(),
            old_start,
            old_lines: base_lines[old_start..old_start + old_len].to_vec(),
            adds_secret: has_secret(&new_lines.concat()),
            new_lines,
            exec_surface: false,
        });
    }
    for h in &mut hunks {
        h.exec_surface = touches_exec_surface(rel, &base_lines, h);
    }
    Ok(FileProposal {
        path: rel.to_string(),
        base_hash,
        hunks,
    })
}

const EXEC_NAMES: &[&str] = &[
    "makefile",
    "gnumakefile",
    "build.rs",
    "lefthook.yml",
    "lefthook.yaml",
    ".pre-commit-config.yaml",
    "conftest.py",
    "pytest.ini",
    "tox.ini",
];
const EXEC_PREFIXES: &[&str] = &[".husky/", ".github/workflows/"];
const TEST_CONFIGS: &[&str] = &[
    "jest.config",
    "vitest.config",
    "vite.config",
    "playwright.config",
    "karma.conf",
    ".mocharc",
    "phpunit.xml",
    "cypress.config",
];

/// True when a hunk touches what a check runs: `package.json` scripts, a Makefile, `build.rs`, hooks, workflows, test configs.
pub fn touches_exec_surface(rel: &str, base_lines: &[String], hunk: &Hunk) -> bool {
    let rel = rel.trim_start_matches("./");
    let name = Path::new(rel)
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("")
        .to_lowercase();
    if EXEC_NAMES.contains(&name.as_str())
        || EXEC_PREFIXES
            .iter()
            .any(|p| rel.starts_with(p) || rel.contains(&format!("/{p}")))
        || TEST_CONFIGS.iter().any(|p| name.starts_with(p))
    {
        return true;
    }
    if name == "package.json" {
        if hunk
            .new_lines
            .iter()
            .chain(hunk.old_lines.iter())
            .any(|l| l.contains("\"scripts\""))
        {
            return true;
        }
        // lines of the `"scripts": { … }` block in the base
        let mut depth = 0i32;
        let mut start = None;
        for (i, l) in base_lines.iter().enumerate() {
            if start.is_none() && l.contains("\"scripts\"") {
                start = Some(i);
                depth = 0;
            }
            if let Some(s) = start {
                depth += l.matches('{').count() as i32 - l.matches('}').count() as i32;
                if depth <= 0 && i >= s {
                    let end = i;
                    let old_end = hunk.old_start + hunk.old_lines.len();
                    // overlaps the block, or inserts inside it (before the closing brace)
                    return hunk.old_start <= end && old_end.max(hunk.old_start + 1) > s;
                }
            }
        }
    }
    false
}

/// True when an approved hunk changes what a check runs. Recomputed from the file on disk: the flag a hunk carries
/// comes from the webview and is never trusted for the « ask again » rule.
pub fn approved_exec_surface(repo: &Repo, file: &FileProposal, approved: &[usize]) -> bool {
    let base = fs::read_to_string(repo.root().join(&file.path))
        .map(|t| lines_of(&t))
        .unwrap_or_default();
    file.hunks
        .iter()
        .filter(|h| approved.contains(&h.id))
        .any(|h| h.exec_surface || touches_exec_surface(&file.path, &base, h))
}

/// Applies the approved hunks of one file, exactly. Refuses when the file's base changed (re-propose).
pub fn apply(repo: &Repo, file: &FileProposal, approved: &[usize]) -> Result<Applied, CodeError> {
    // checked at the call …
    repo.resolve_write(&file.path)?;
    let chosen: Vec<&Hunk> = file
        .hunks
        .iter()
        .filter(|h| approved.contains(&h.id))
        .collect();
    let none = Applied {
        path: file.path.clone(),
        written: false,
        hunks_applied: 0,
    };

    // … the base, compared by hash
    let target = repo.root().join(&file.path);
    let current = match fs::read(&target) {
        Ok(b) => Some(b),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => None,
        Err(e) => return Err(CodeError::Io(e.to_string())),
    };
    if current.as_deref().map(hash_bytes) != file.base_hash {
        return Err(CodeError::Conflict(file.path.clone()));
    }
    if chosen.is_empty() {
        return Ok(none);
    }
    let base_text = String::from_utf8(current.unwrap_or_default())
        .map_err(|_| CodeError::Refused(format!("{} is not text", file.path)))?;
    let base = lines_of(&base_text);

    let mut sorted = chosen.clone();
    sorted.sort_by_key(|h| h.old_start);
    let mut out: Vec<String> = Vec::new();
    let mut at = 0usize;
    for h in &sorted {
        let end = h.old_start + h.old_lines.len();
        if h.old_start < at || end > base.len() || base[h.old_start..end] != h.old_lines[..] {
            return Err(CodeError::Conflict(file.path.clone()));
        }
        out.extend_from_slice(&base[at..h.old_start]);
        out.extend(h.new_lines.iter().cloned());
        at = end;
    }
    out.extend_from_slice(&base[at..]);

    // … and again right before the write (a symlink or a swap during the run).
    let target = repo.resolve_write(&file.path)?;
    if let Some(parent) = target.parent() {
        fs::create_dir_all(parent).map_err(|e| CodeError::Io(e.to_string()))?;
        repo.resolve_write(&file.path)?;
    }
    let tmp = target.with_file_name(format!(".orazaka-{}.tmp", std::process::id()));
    fs::write(&tmp, out.concat()).map_err(|e| CodeError::Io(e.to_string()))?;
    if let Ok(md) = fs::metadata(&target) {
        let _ = fs::set_permissions(&tmp, md.permissions());
    }
    fs::rename(&tmp, &target).map_err(|e| {
        let _ = fs::remove_file(&tmp);
        CodeError::Io(e.to_string())
    })?;
    Ok(Applied {
        path: file.path.clone(),
        written: true,
        hunks_applied: sorted.len(),
    })
}
