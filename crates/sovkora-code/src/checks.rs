//! Checks: the one narrow `exec` of ADR-093 §4.4 (it amends ADR-079 §4).
//!
//! A check is `{ name, argv[], cwd, timeout, network }`. The repository may *suggest* checks, but only a check the user
//! has confirmed in the app (a listed, revocable grant, once per repository and device) can run, and the model asks for
//! it **by name**. A run uses argv with no shell, the repository as cwd, an environment built from an allow-list, and a
//! Seatbelt profile (`sandbox-exec`): writes only under the repository, the run's own temp directory and the toolchain
//! caches, no network unless the confirmed check declares it. Without `sandbox-exec` nothing runs.

use super::diff::hash_bytes;
use super::scope::{CodeError, Repo};
use super::visibility::redact;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

// @fixed: hard ceilings of the settings rule set; a setting can lower a limit, never raise it past these.
pub const MAX_RUNS_PER_SESSION: usize = 100;
pub const MAX_WALL_SECS: u64 = 3600;
pub const MAX_OUTPUT: usize = 256 * 1024;
// @fixed: the timeout a suggestion gets when it names none.
const DEFAULT_TIMEOUT_SECS: u64 = 600;
const MAX_ARGV: usize = 64;
const MAX_ARG_LEN: usize = 4096;
const SUGGEST_FILE_CAP: u64 = 64 * 1024;
const SANDBOX_EXEC: &str = "/usr/bin/sandbox-exec";

#[derive(Debug, PartialEq, Eq)]
pub enum CheckError {
    /// The name is not a confirmed check of this repository (or its confirmation was revoked).
    NotConfirmed(String),
    Invalid(String),
    /// The session is tainted and a « changes what runs » hunk was applied since the last approval.
    ApprovalRequired,
    LimitReached(String),
    /// No Seatbelt on this machine: the check is refused rather than run unsandboxed.
    SandboxUnavailable,
    Code(CodeError),
    Io(String),
}

impl std::fmt::Display for CheckError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            CheckError::NotConfirmed(n) => write!(f, "not a confirmed check: {n}"),
            CheckError::Invalid(m) => write!(f, "invalid check: {m}"),
            CheckError::ApprovalRequired => write!(
                f,
                "approval required: a change to what runs was applied since the last approval"
            ),
            CheckError::LimitReached(m) => write!(f, "limit reached: {m}"),
            CheckError::SandboxUnavailable => {
                write!(f, "the sandbox is unavailable, the check was not run")
            }
            CheckError::Code(e) => write!(f, "{e}"),
            CheckError::Io(e) => write!(f, "io: {e}"),
        }
    }
}

impl std::error::Error for CheckError {}

impl From<CodeError> for CheckError {
    fn from(e: CodeError) -> Self {
        CheckError::Code(e)
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Check {
    pub name: String,
    pub argv: Vec<String>,
    /// Relative to the repository root; empty means the root.
    #[serde(default)]
    pub cwd: String,
    #[serde(default = "default_timeout")]
    pub timeout_secs: u64,
    #[serde(default)]
    pub network: bool,
    /// Names of extra environment variables the check wants, copied from the app's environment when present.
    #[serde(default)]
    pub env: Vec<String>,
}

fn default_timeout() -> u64 {
    DEFAULT_TIMEOUT_SECS
}

/// The `exec` grant: listed, revocable, audited (the caller appends the audit events).
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Grant {
    pub id: String,
    pub repo: String,
    pub check: Check,
    pub argv_hash: String,
    pub created_at: String,
    #[serde(default)]
    pub revoked_at: Option<String>,
}

/// What the run loop knows about the session (ADR-093 §4.3: a code session is tainted from its first untrusted byte).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Session {
    pub tainted: bool,
    runs: usize,
    exec_pending: bool,
}

impl Default for Session {
    fn default() -> Self {
        Session {
            tainted: true,
            runs: 0,
            exec_pending: false,
        }
    }
}

impl Session {
    /// A hunk was applied; `exec_surface` says it changes what a check runs.
    pub fn note_applied(&mut self, exec_surface: bool) {
        if exec_surface {
            self.exec_pending = true;
        }
    }

    pub fn needs_approval(&self) -> bool {
        self.tainted && self.exec_pending
    }

    pub fn runs(&self) -> usize {
        self.runs
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Limits {
    pub max_runs: usize,
    pub max_wall_secs: u64,
    pub output_cap: usize,
}

impl Limits {
    /// The settings rule set's values, clamped to what the code allows.
    pub fn bounded(max_runs: usize, max_wall_secs: u64, output_cap: usize) -> Limits {
        Limits {
            max_runs: max_runs.clamp(1, MAX_RUNS_PER_SESSION),
            max_wall_secs: max_wall_secs.clamp(1, MAX_WALL_SECS),
            output_cap: output_cap.clamp(1024, MAX_OUTPUT),
        }
    }
}

impl Default for Limits {
    fn default() -> Self {
        Limits::bounded(20, 900, 64 * 1024)
    }
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct CheckRun {
    pub name: String,
    pub exit_code: Option<i32>,
    pub duration_ms: u64,
    pub timed_out: bool,
    pub argv_hash: String,
    /// Redacted and capped; fed back to the agent as data. Never written to the audit.
    pub output: String,
    pub truncated: bool,
}

impl CheckRun {
    /// What the audit records of a run: no output.
    pub fn audit_data(&self) -> Value {
        json!({
            "name": self.name,
            "exitCode": self.exit_code,
            "durationMs": self.duration_ms,
            "timedOut": self.timed_out,
            "argvHash": self.argv_hash,
        })
    }
}

// ---- the confirmed checks ---------------------------------------------------------------------------------------

#[derive(Serialize, Deserialize, Default)]
struct BookFile {
    grants: Vec<Grant>,
}

/// The device's `exec` grants and the session's run state.
pub struct Book {
    path: Option<PathBuf>,
    grants: Vec<Grant>,
    pub session: Session,
}

impl Book {
    pub fn in_memory() -> Book {
        Book {
            path: None,
            grants: vec![],
            session: Session::default(),
        }
    }

    /// A new run is a new session: tainted from its first untrusted byte, nothing pending, no runs counted.
    pub fn begin_run(&mut self) {
        self.session = Session::default();
    }

    pub fn open(path: &Path) -> Book {
        let grants = fs::read_to_string(path)
            .ok()
            .and_then(|s| serde_json::from_str::<BookFile>(&s).ok())
            .unwrap_or_default()
            .grants;
        Book {
            path: Some(path.to_path_buf()),
            grants,
            session: Session::default(),
        }
    }

    fn save(&self) -> Result<(), CheckError> {
        let Some(path) = &self.path else {
            return Ok(());
        };
        if let Some(dir) = path.parent() {
            fs::create_dir_all(dir).map_err(|e| CheckError::Io(e.to_string()))?;
        }
        let body = serde_json::to_string_pretty(&BookFile {
            grants: self.grants.clone(),
        })
        .map_err(|e| CheckError::Io(e.to_string()))?;
        let tmp = path.with_extension("json.tmp");
        fs::write(&tmp, body).map_err(|e| CheckError::Io(e.to_string()))?;
        fs::rename(&tmp, path).map_err(|e| CheckError::Io(e.to_string()))
    }

    /// The user confirms a check for this repository. Replaces the earlier confirmation of the same name.
    pub fn confirm(&mut self, repo: &Repo, check: Check, at: &str) -> Result<Grant, CheckError> {
        validate(repo, &check)?;
        let root = repo.root().display().to_string();
        for g in self
            .grants
            .iter_mut()
            .filter(|g| g.repo == root && g.check.name == check.name && g.revoked_at.is_none())
        {
            g.revoked_at = Some(at.to_string());
        }
        let grant = Grant {
            id: uuid::Uuid::new_v4().to_string(),
            repo: root,
            argv_hash: argv_hash(&check.argv),
            check,
            created_at: at.to_string(),
            revoked_at: None,
        };
        self.grants.push(grant.clone());
        self.save()?;
        Ok(grant)
    }

    pub fn revoke(&mut self, id: &str, at: &str) -> bool {
        let Some(g) = self
            .grants
            .iter_mut()
            .find(|g| g.id == id && g.revoked_at.is_none())
        else {
            return false;
        };
        g.revoked_at = Some(at.to_string());
        self.save().is_ok()
    }

    /// The active grants of a repository.
    pub fn list(&self, repo: &Repo) -> Vec<&Grant> {
        let root = repo.root().display().to_string();
        self.grants
            .iter()
            .filter(|g| g.repo == root && g.revoked_at.is_none())
            .collect()
    }

    fn confirmed(&self, repo: &Repo, name: &str) -> Option<Check> {
        self.list(repo)
            .into_iter()
            .find(|g| g.check.name == name)
            .map(|g| g.check.clone())
    }
}

pub fn argv_hash(argv: &[String]) -> String {
    hash_bytes(argv.join("\u{1f}").as_bytes())
}

fn valid_name(name: &str) -> bool {
    let b = name.as_bytes();
    !b.is_empty()
        && b.len() <= 64
        && (b[0].is_ascii_lowercase() || b[0].is_ascii_digit())
        && b.iter().all(|c| {
            c.is_ascii_lowercase() || c.is_ascii_digit() || matches!(c, b'.' | b'_' | b'-')
        })
}

const CREDENTIAL_WORDS: &[&str] = &[
    "TOKEN",
    "SECRET",
    "PASSWORD",
    "PASSWD",
    "CREDENTIAL",
    "API_KEY",
    "APIKEY",
    "PRIVATE",
    "AUTH",
    "SESSION",
    "COOKIE",
];
const CREDENTIAL_PREFIXES: &[&str] = &[
    "GH_",
    "GITHUB_",
    "ORAZAKA_",
    "OZ_",
    "AWS_",
    "SSH_",
    "GPG_",
    "OPENAI_",
    "ANTHROPIC_",
    "DYLD_",
    "LD_",
];
const BASE_ENV: &[&str] = &["PATH", "HOME", "LANG", "TMPDIR"];

fn env_name_allowed(name: &str) -> bool {
    let up = name.to_ascii_uppercase();
    !name.is_empty()
        && name.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_')
        && !BASE_ENV.contains(&up.as_str())
        && !CREDENTIAL_WORDS.iter().any(|w| up.contains(w))
        && !up.ends_with("_KEY")
        && !CREDENTIAL_PREFIXES.iter().any(|p| up.starts_with(p))
}

fn resolve_cwd(repo: &Repo, cwd: &str) -> Result<PathBuf, CheckError> {
    if cwd.is_empty() || cwd == "." {
        return Ok(repo.root().to_path_buf());
    }
    let dir = repo.resolve_read(cwd)?;
    if !dir.is_dir() {
        return Err(CheckError::Invalid(format!("cwd is not a folder: {cwd}")));
    }
    Ok(dir)
}

pub fn validate(repo: &Repo, c: &Check) -> Result<(), CheckError> {
    let bad = |m: &str| Err(CheckError::Invalid(m.to_string()));
    if !valid_name(&c.name) {
        return bad("the name is a short identifier (a-z, 0-9, . _ -)");
    }
    if c.argv.is_empty() || c.argv[0].is_empty() {
        return bad("argv is empty");
    }
    if c.argv.len() > MAX_ARGV
        || c.argv
            .iter()
            .any(|a| a.len() > MAX_ARG_LEN || a.contains('\0'))
    {
        return bad("argv is too long");
    }
    if c.timeout_secs == 0 || c.timeout_secs > MAX_WALL_SECS {
        return bad("the timeout is between 1 s and the ceiling");
    }
    if let Some(n) = c.env.iter().find(|n| !env_name_allowed(n)) {
        return Err(CheckError::Invalid(format!(
            "the variable {n} cannot be declared"
        )));
    }
    resolve_cwd(repo, &c.cwd).map(|_| ())
}

// ---- suggestions ------------------------------------------------------------------------------------------------

fn suggestion(name: &str, argv: &[&str]) -> Check {
    Check {
        name: name.into(),
        argv: argv.iter().map(|s| s.to_string()).collect(),
        cwd: String::new(),
        timeout_secs: DEFAULT_TIMEOUT_SECS,
        network: false,
        env: vec![],
    }
}

fn read_small(repo: &Repo, rel: &str) -> Option<String> {
    let path = repo.resolve_read(rel).ok()?;
    if fs::metadata(&path).ok()?.len() > SUGGEST_FILE_CAP {
        return None;
    }
    fs::read_to_string(path).ok()
}

/// Checks the repository suggests. They are proposals for the user to confirm, never an allow-list.
pub fn suggest(repo: &Repo) -> Vec<Check> {
    let mut out: Vec<Check> = Vec::new();
    if let Some(pkg) =
        read_small(repo, "package.json").and_then(|s| serde_json::from_str::<Value>(&s).ok())
    {
        for key in ["test", "lint", "typecheck", "check", "build"] {
            if pkg.get("scripts").and_then(|s| s.get(key)).is_some() {
                out.push(suggestion(&format!("npm-{key}"), &["npm", "run", key]));
            }
        }
    }
    if read_small(repo, "Cargo.toml").is_some() {
        out.push(suggestion("cargo-test", &["cargo", "test"]));
    }
    if read_small(repo, "pom.xml").is_some() {
        out.push(suggestion("mvn-test", &["mvn", "-B", "test"]));
    }
    if let Some(Value::Array(items)) = read_small(repo, ".orazaka/checks.json")
        .and_then(|s| serde_json::from_str::<Value>(&s).ok())
    {
        out.extend(
            items
                .into_iter()
                .filter_map(|v| serde_json::from_value::<Check>(v).ok()),
        );
    }
    let mut seen = std::collections::HashSet::new();
    out.retain(|c| validate(repo, c).is_ok() && seen.insert(c.name.clone()));
    out
}

// ---- the environment and the profile ----------------------------------------------------------------------------

/// The environment of a run: `PATH`, `HOME`, `LANG`, a private `TMPDIR`, and what the check declares. Nothing else.
pub fn build_env(
    check: &Check,
    get: &dyn Fn(&str) -> Option<String>,
    tmp: &Path,
) -> Vec<(String, String)> {
    let home = get("HOME").unwrap_or_default();
    let mut path: Vec<String> = get("PATH")
        .unwrap_or_else(|| "/usr/bin:/bin".into())
        .split(':')
        .filter(|p| !p.is_empty())
        .map(str::to_string)
        .collect();
    // an app started from the Dock has a minimal PATH: add the usual toolchain folders
    let mut extras = vec![
        "/opt/homebrew/bin".to_string(),
        "/usr/local/bin".to_string(),
    ];
    if !home.is_empty() {
        extras.push(format!("{home}/.cargo/bin"));
    }
    for extra in extras {
        if !path.contains(&extra) {
            path.push(extra);
        }
    }
    let mut env = vec![
        ("PATH".to_string(), path.join(":")),
        ("HOME".to_string(), home),
        (
            "LANG".to_string(),
            get("LANG").unwrap_or_else(|| "en_US.UTF-8".into()),
        ),
        ("TMPDIR".to_string(), tmp.display().to_string()),
    ];
    for name in check.env.iter().filter(|n| env_name_allowed(n)) {
        if let Some(v) = get(name) {
            env.push((name.clone(), v));
        }
    }
    env
}

/// Folders a toolchain writes its caches to (only those that exist).
fn cache_dirs(home: &Path) -> Vec<PathBuf> {
    [
        ".cargo",
        ".rustup",
        ".npm",
        ".pnpm-store",
        ".m2",
        ".gradle",
        ".cache",
        "Library/Caches",
        "Library/pnpm",
    ]
    .iter()
    .filter_map(|d| home.join(d).canonicalize().ok())
    .collect()
}

/// The Seatbelt profile. Paths come in as parameters (`-D`), never spliced into the text.
pub fn seatbelt_profile(cache_count: usize, network: bool) -> String {
    let mut p = String::from(
        "(version 1)\n(allow default)\n(deny file-write*)\n(allow file-write*\n  (subpath (param \"REPO\"))\n  (subpath (param \"RUNTMP\"))\n  (literal \"/dev/null\")\n  (literal \"/dev/tty\")\n  (literal \"/dev/dtracehelper\")\n",
    );
    for i in 0..cache_count {
        p.push_str(&format!("  (subpath (param \"CACHE{i}\"))\n"));
    }
    p.push_str(")\n");
    // a check never plants a hook or edits the git config: the runner commits unsandboxed
    p.push_str(
        "(deny file-write*\n  (subpath (param \"GITHOOKS\"))\n  (literal (param \"GITCONFIG\")))\n",
    );
    if !network {
        p.push_str("(deny network*)\n");
    }
    p
}

// ---- the run ----------------------------------------------------------------------------------------------------

/// Runs a confirmed check by **name**. `approved` is the user's approval of this run.
pub fn run(
    repo: &Repo,
    book: &mut Book,
    name: &str,
    approved: bool,
    limits: &Limits,
) -> Result<CheckRun, CheckError> {
    let check = book
        .confirmed(repo, name)
        .ok_or_else(|| CheckError::NotConfirmed(name.to_string()))?;
    if book.session.needs_approval() && !approved {
        return Err(CheckError::ApprovalRequired);
    }
    if book.session.runs >= limits.max_runs {
        return Err(CheckError::LimitReached(format!(
            "{} runs in this session",
            limits.max_runs
        )));
    }
    validate(repo, &check)?; // the folder may have changed since the confirmation
    let cwd = resolve_cwd(repo, &check.cwd)?;
    if !Path::new(SANDBOX_EXEC).exists() {
        return Err(CheckError::SandboxUnavailable);
    }
    if approved {
        book.session.exec_pending = false;
    }
    book.session.runs += 1;

    let runtmp = std::env::temp_dir().join(format!("orazaka-check-{}", uuid::Uuid::new_v4()));
    fs::create_dir_all(&runtmp).map_err(|e| CheckError::Io(e.to_string()))?;
    let runtmp_path = runtmp
        .canonicalize()
        .map_err(|e| CheckError::Io(e.to_string()))?;
    let home = std::env::var("HOME").map(PathBuf::from).unwrap_or_default();
    let caches = cache_dirs(&home);
    let env = build_env(&check, &|k| std::env::var(k).ok(), &runtmp_path);

    let mut cmd = Command::new(SANDBOX_EXEC);
    cmd.arg("-D").arg(format!("REPO={}", repo.root().display()));
    cmd.arg("-D")
        .arg(format!("RUNTMP={}", runtmp_path.display()));
    cmd.arg("-D").arg(format!(
        "GITHOOKS={}",
        repo.root().join(".git/hooks").display()
    ));
    cmd.arg("-D").arg(format!(
        "GITCONFIG={}",
        repo.root().join(".git/config").display()
    ));
    for (i, c) in caches.iter().enumerate() {
        cmd.arg("-D").arg(format!("CACHE{i}={}", c.display()));
    }
    cmd.arg("-p")
        .arg(seatbelt_profile(caches.len(), check.network));
    cmd.arg("--").args(&check.argv);
    cmd.current_dir(&cwd)
        .env_clear()
        .envs(env)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        cmd.process_group(0);
    }

    let started = Instant::now();
    let mut child = cmd.spawn().map_err(|e| CheckError::Io(e.to_string()))?;
    let pid = child.id() as i32;
    let buf = Arc::new(Mutex::new((Vec::<u8>::new(), false)));
    let readers: Vec<_> = [
        child
            .stdout
            .take()
            .map(|s| Box::new(s) as Box<dyn Read + Send>),
        child
            .stderr
            .take()
            .map(|s| Box::new(s) as Box<dyn Read + Send>),
    ]
    .into_iter()
    .flatten()
    .map(|mut stream| {
        let buf = buf.clone();
        let cap = limits.output_cap;
        std::thread::spawn(move || {
            let mut chunk = [0u8; 8192];
            while let Ok(n) = stream.read(&mut chunk) {
                if n == 0 {
                    break;
                }
                let mut g = buf.lock().unwrap();
                let room = cap.saturating_sub(g.0.len());
                g.0.extend_from_slice(&chunk[..n.min(room)]);
                if n > room {
                    g.1 = true; // keep draining so the child never blocks on a full pipe
                }
            }
        })
    })
    .collect();

    let wall = Duration::from_secs(check.timeout_secs.min(limits.max_wall_secs));
    let mut timed_out = false;
    let status = loop {
        match child.try_wait() {
            Ok(Some(s)) => break Some(s),
            Ok(None) if started.elapsed() >= wall => {
                timed_out = true;
                kill_group(pid);
                break child.wait().ok();
            }
            Ok(None) => std::thread::sleep(Duration::from_millis(20)),
            Err(_) => break None,
        }
    };
    // stray background processes of a finished check do not outlive it
    kill_group(pid);
    for r in readers {
        let _ = r.join();
    }
    let _ = fs::remove_dir_all(&runtmp_path);
    let (bytes, truncated) = std::mem::take(&mut *buf.lock().unwrap());
    Ok(CheckRun {
        name: check.name.clone(),
        exit_code: status.and_then(|s| s.code()),
        duration_ms: started.elapsed().as_millis() as u64,
        timed_out,
        argv_hash: argv_hash(&check.argv),
        output: redact(&String::from_utf8_lossy(&bytes)),
        truncated,
    })
}

fn kill_group(pid: i32) {
    // SAFETY: kill(2) with a negative pid signals the process group the run created with `process_group(0)`.
    unsafe {
        libc::kill(-pid, libc::SIGKILL);
    }
}
