//! The visibility filter (one function, used by every read) and the redactor (ADR-093 §4.6).

use super::scope::Repo;
use regex::Regex;
use std::collections::HashSet;
use std::io::Write;
use std::process::{Command, Stdio};
use std::sync::OnceLock;

/// `.git/**`, at any depth.
pub fn is_git_internal(rel: &str) -> bool {
    rel.split('/').any(|c| c == ".git")
}

const SECRET_DIRS: &[&str] = &[".ssh", ".gnupg", ".aws", ".kube"];
const SECRET_NAMES: &[&str] = &[
    ".npmrc",
    ".pypirc",
    ".netrc",
    ".pgpass",
    ".htpasswd",
    ".git-credentials",
    "credentials",
    "credentials.json",
    "kubeconfig",
    "id_rsa",
    "id_dsa",
    "id_ecdsa",
    "id_ed25519",
    "secrets.json",
    "secrets.yaml",
    "secrets.yml",
    "terraform.tfvars",
    "application_default_credentials.json",
];
const SECRET_SUFFIXES: &[&str] = &[
    ".pem",
    ".p12",
    ".pfx",
    ".key",
    ".keystore",
    ".jks",
    ".tfstate",
    ".kdbx",
];
const DOTENV_SAFE: &[&str] = &[".env.example", ".env.sample", ".env.template", ".env.dist"];

/// The secret rule set: returns the reason when a path (relative, `/`-separated) matches a secret pattern.
pub fn is_secret_path(rel: &str) -> Option<&'static str> {
    let comps: Vec<&str> = rel.split('/').collect();
    if comps.iter().any(|c| SECRET_DIRS.contains(c)) {
        return Some("secret directory");
    }
    let name = comps.last().copied().unwrap_or("").to_lowercase();
    if name == ".env" || (name.starts_with(".env.") && !DOTENV_SAFE.contains(&name.as_str())) {
        return Some("dotenv file");
    }
    if SECRET_NAMES.contains(&name.as_str())
        || (name.starts_with("service-account") && name.ends_with(".json"))
    {
        return Some("credential file");
    }
    if SECRET_SUFFIXES.iter().any(|s| name.ends_with(s)) {
        return Some("key or certificate");
    }
    None
}

/// Gitignored paths among `paths`, even when tracked (`--no-index`).
fn ignored(repo: &Repo, paths: &[String]) -> HashSet<String> {
    if paths.is_empty() {
        return HashSet::new();
    }
    let Ok(mut child) = Command::new("git")
        .arg("-C")
        .arg(repo.root())
        .args(["check-ignore", "--no-index", "-z", "--stdin"])
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
    else {
        return HashSet::new();
    };
    let mut input = Vec::new();
    for p in paths {
        input.extend_from_slice(p.as_bytes());
        input.push(0);
    }
    let mut stdin = child.stdin.take().expect("piped");
    let writer = std::thread::spawn(move || {
        let _ = stdin.write_all(&input);
    });
    let out = child.wait_with_output();
    let _ = writer.join();
    match out {
        Ok(o) => o
            .stdout
            .split(|b| *b == 0)
            .filter(|s| !s.is_empty())
            .map(|s| String::from_utf8_lossy(s).to_string())
            .collect(),
        Err(_) => HashSet::new(),
    }
}

/// The visible subset of `paths`, in order. The single filter behind every read tool.
pub fn filter_visible(repo: &Repo, paths: &[String]) -> Vec<String> {
    let candidates: Vec<String> = paths
        .iter()
        .filter(|p| !is_git_internal(p) && is_secret_path(p).is_none())
        .cloned()
        .collect();
    let hidden = ignored(repo, &candidates);
    candidates
        .into_iter()
        .filter(|p| !hidden.contains(p))
        .collect()
}

pub fn is_visible(repo: &Repo, rel: &str) -> bool {
    !filter_visible(repo, &[rel.to_string()]).is_empty()
}

fn token_patterns() -> &'static Vec<Regex> {
    static P: OnceLock<Vec<Regex>> = OnceLock::new();
    P.get_or_init(|| {
        [
            r"-----BEGIN [A-Z0-9 ]*-----[\s\S]*?-----END [A-Z0-9 ]*-----",
            r"github_pat_[A-Za-z0-9_]{20,}",
            r"gh[pousr]_[A-Za-z0-9]{20,}",
            r"\boz_[A-Za-z0-9_\-]{16,}",
            r"\bsk-[A-Za-z0-9_\-]{16,}",
            r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b",
        ]
        .iter()
        .map(|r| Regex::new(r).expect("static pattern"))
        .collect()
    })
}

pub const REDACTED: &str = "[redacted]";

/// Removes known token shapes before any text leaves the runner.
pub fn redact(text: &str) -> String {
    let mut out = text.to_string();
    for re in token_patterns() {
        out = re.replace_all(&out, REDACTED).into_owned();
    }
    out
}

/// True when `text` holds a token-shaped string (a diff that introduces one is flagged).
pub fn has_secret(text: &str) -> bool {
    token_patterns().iter().any(|re| re.is_match(text))
}
