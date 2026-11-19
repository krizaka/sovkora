//! The read tools (effect `read`): `read_file`, `list_dir`, `search_text`, `outline` (ADR-093 §4.2).
//! Paths go through `Repo::resolve_read`; text goes through `redact` before it is returned.

use super::scope::{CodeError, Repo};
use super::visibility::{filter_visible, redact};
use regex::Regex;
use serde::Serialize;
use std::fs;
use std::io::Read;

// @fixed: size caps of one tool answer, so a single read cannot flood a prompt.
pub const READ_CAP: usize = 256 * 1024;
pub const SEARCH_CAP: usize = 200;
const SEARCH_FILE_CAP: u64 = 1024 * 1024;

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct FileText {
    pub path: String,
    pub text: String,
    pub truncated: bool,
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
pub struct Entry {
    pub name: String,
    pub dir: bool,
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
pub struct Match {
    pub path: String,
    pub line: usize,
    pub text: String,
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
pub struct Symbol {
    pub line: usize,
    pub text: String,
}

pub fn read_file(repo: &Repo, rel: &str) -> Result<FileText, CodeError> {
    let path = repo.resolve_read(rel)?;
    let file = fs::File::open(&path).map_err(|e| CodeError::Io(e.to_string()))?;
    let mut buf = Vec::new();
    file.take(READ_CAP as u64 + 1)
        .read_to_end(&mut buf)
        .map_err(|e| CodeError::Io(e.to_string()))?;
    let truncated = buf.len() > READ_CAP;
    buf.truncate(READ_CAP);
    let text = match String::from_utf8(buf) {
        Ok(t) => t,
        Err(e) if truncated => String::from_utf8_lossy(e.as_bytes()).to_string(),
        Err(_) => return Err(CodeError::Refused(format!("{rel} is not text"))),
    };
    Ok(FileText {
        path: rel.to_string(),
        text: redact(&text),
        truncated,
    })
}

pub fn list_dir(repo: &Repo, rel: &str) -> Result<Vec<Entry>, CodeError> {
    let dir = if rel.is_empty() || rel == "." {
        repo.root().to_path_buf()
    } else {
        repo.resolve_read(rel)?
    };
    let prefix = dir
        .strip_prefix(repo.root())
        .map(|p| p.to_string_lossy().to_string())
        .unwrap_or_default();
    let mut names = Vec::new();
    for e in fs::read_dir(&dir).map_err(|e| CodeError::Io(e.to_string()))? {
        let e = e.map_err(|e| CodeError::Io(e.to_string()))?;
        let name = e.file_name().to_string_lossy().to_string();
        names.push(if prefix.is_empty() {
            name
        } else {
            format!("{prefix}/{name}")
        });
    }
    let mut out: Vec<Entry> = filter_visible(repo, &names)
        .into_iter()
        .filter_map(|full| {
            let real = repo.root().join(&full).canonicalize().ok()?;
            // an entry whose symlink leaves the repository is not shown
            if !real.starts_with(repo.root()) {
                return None;
            }
            Some(Entry {
                name: full.rsplit('/').next().unwrap_or(&full).to_string(),
                dir: real.is_dir(),
            })
        })
        .collect();
    out.sort_by(|a, b| b.dir.cmp(&a.dir).then(a.name.cmp(&b.name)));
    Ok(out)
}

/// Literal, case-sensitive search over the visible files.
pub fn search_text(repo: &Repo, query: &str) -> Result<Vec<Match>, CodeError> {
    if query.is_empty() {
        return Ok(vec![]);
    }
    let mut out = Vec::new();
    for rel in filter_visible(repo, &repo.candidate_files()?) {
        let Ok(path) = repo.resolve_read(&rel) else {
            continue;
        };
        if fs::metadata(&path)
            .map(|m| !m.is_file() || m.len() > SEARCH_FILE_CAP)
            .unwrap_or(true)
        {
            continue;
        }
        let Ok(text) = fs::read_to_string(&path) else {
            continue;
        };
        for (i, line) in text.lines().enumerate() {
            if line.contains(query) {
                out.push(Match {
                    path: rel.clone(),
                    line: i + 1,
                    text: redact(line.trim()),
                });
                if out.len() >= SEARCH_CAP {
                    return Ok(out);
                }
            }
        }
    }
    Ok(out)
}

/// The declarations of a file (functions, types, classes), by line. A light, language-agnostic outline.
pub fn outline(repo: &Repo, rel: &str) -> Result<Vec<Symbol>, CodeError> {
    let file = read_file(repo, rel)?;
    let re = Regex::new(
        r"^\s*(?:pub(?:\([a-z]+\))?\s+|export\s+(?:default\s+)?|public\s+|private\s+|protected\s+|static\s+|async\s+|abstract\s+|final\s+)*(?:fn|struct|enum|trait|impl|mod|class|interface|type|function|def|func|const|record)\s+\S",
    )
    .expect("static pattern");
    Ok(file
        .text
        .lines()
        .enumerate()
        .filter(|(_, l)| re.is_match(l))
        .map(|(i, l)| Symbol {
            line: i + 1,
            text: l.trim().to_string(),
        })
        .collect())
}
