//! Sovkora Desktop — a control panel and a least-privilege file bridge for Sovkora (macOS).
//!
//! * `sidecar`   — the allow-list of operations of the bundled `orazaka` CLI (no shell, no free-form args).
//! * `scope`     — the folders the user granted; every read of a file goes through it.
//! * `files`     — listing / preview of granted files.
//! * `settings`  — connect mode, the remote Sovkora, the workspace folder.
//! * `automation` — scheduled automations run on this Mac, tools over MCP behind approvals (ADR-079).

pub mod automation;
pub mod code;
// The folder scope and the files inside a grant are krizaka-tauri-kit's (ADR-005: one implementation, tested once).
pub use krizaka_rust::{files, scope};
pub mod settings;
pub mod sidecar;

use files::{Entry, Listing, Preview};
use krizaka_rust::access::Access;
use notify::{EventKind, RecommendedWatcher, RecursiveMode, Watcher};
use scope::{ScopeStore, ScopedPath};
use serde::Serialize;
use settings::Settings;
use sidecar::CliCall;
use std::collections::HashMap;
use std::io::{BufRead, BufReader};
use std::net::{SocketAddr, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tauri::menu::{Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::TrayIconBuilder;
use tauri::{
    AppHandle, DragDropEvent, Emitter, Manager, RunEvent, State, WebviewUrl, WebviewWindowBuilder,
    WindowEvent,
};
use tauri_plugin_autostart::{MacosLauncher, ManagerExt};
use tauri_plugin_dialog::DialogExt;

const WEB_PORT: u16 = 3000;
const EDGE_PORT: u16 = 8088;
const TRAY_OFF: &[u8] = include_bytes!("../icons/tray-off.png");
const TRAY_ON: &[u8] = include_bytes!("../icons/tray-on.png");

#[derive(Default)]
struct Drops {
    files: Vec<PathBuf>,
    folders: Vec<PathBuf>,
}

struct TrayItems {
    status: MenuItem<tauri::Wry>,
    panel: MenuItem<tauri::Wry>,
    workspace: MenuItem<tauri::Wry>,
    start: MenuItem<tauri::Wry>,
    stop: MenuItem<tauri::Wry>,
    quit: MenuItem<tauri::Wry>,
}

pub struct AppState {
    data_dir: PathBuf,
    settings: Mutex<Settings>,
    scope: Arc<Mutex<ScopeStore>>,
    automations: Arc<automation::Service>,
    drops: Mutex<Drops>,
    watchers: Mutex<HashMap<PathBuf, RecommendedWatcher>>,
    busy: Mutex<bool>,
    workspace_origin: Arc<Mutex<String>>,
    tray: Mutex<Option<TrayItems>>,
    last_probe: Mutex<String>,
    /// The repository open in the Code tab (ADR-093).
    code: Mutex<Option<code::Repo>>,
    /// The confirmed checks of this device and the Code session's run state (ADR-093 §4.4).
    code_checks: Mutex<code::checks::Book>,
    /// The settings rule set for code runs (`code-rules.json`): check limits and the agent's budgets.
    code_rules: code::rules::CodeRules,
    /// The GitHub App connection (ADR-093 slice 4): the sign-in in progress; the tokens live in the Keychain only.
    github: code::github::Connection,
    github_config: code::github::GithubConfig,
    secrets: Arc<dyn krizaka_rust::secrets::SecretStore>,
}

impl AppState {
    fn settings_file(&self) -> PathBuf {
        self.data_dir.join("settings.json")
    }
    fn scope_file(&self) -> PathBuf {
        self.data_dir.join("scope.json")
    }
    fn persist_scope(&self, scope: &ScopeStore) -> Result<(), String> {
        scope.save(&self.scope_file()).map_err(|e| e.to_string())
    }
}

type Res<T> = Result<T, String>;

// ───────────────────────────── state ─────────────────────────────

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Probe {
    web: bool,
    edge: bool,
    state: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Snapshot {
    settings: Settings,
    roots: Vec<String>,
    sidecar: bool,
    autostart: bool,
    probe: Probe,
    app_version: String,
}

fn port_open(port: u16) -> bool {
    let addr: SocketAddr = ([127, 0, 0, 1], port).into();
    TcpStream::connect_timeout(&addr, Duration::from_millis(300)).is_ok()
}

fn probe() -> Probe {
    let (web, edge) = (port_open(WEB_PORT), port_open(EDGE_PORT));
    let state = match (web, edge) {
        (true, true) => "running",
        (false, false) => "stopped",
        _ => "partial",
    };
    Probe {
        web,
        edge,
        state: state.into(),
    }
}

fn sidecar_path() -> Option<PathBuf> {
    let mut candidates: Vec<PathBuf> = Vec::new();
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            candidates.push(dir.join("orazaka"));
        }
    }
    candidates
        .push(Path::new(env!("CARGO_MANIFEST_DIR")).join("binaries/orazaka-aarch64-apple-darwin"));
    candidates.into_iter().find(|p| p.is_file())
}

#[tauri::command]
fn get_snapshot(app: AppHandle, state: State<'_, AppState>) -> Snapshot {
    Snapshot {
        settings: state.settings.lock().unwrap().clone(),
        roots: state
            .scope
            .lock()
            .unwrap()
            .roots()
            .iter()
            .map(|p| p.display().to_string())
            .collect(),
        sidecar: sidecar_path().is_some(),
        autostart: app.autolaunch().is_enabled().unwrap_or(false),
        probe: probe(),
        app_version: app.package_info().version.to_string(),
    }
}

#[tauri::command]
fn probe_stack() -> Probe {
    probe()
}

#[tauri::command]
fn save_settings(app: AppHandle, state: State<'_, AppState>, settings: Settings) -> Res<Settings> {
    let mut settings = settings.validated()?;
    // The watched list is owned by the watcher commands, not by the form.
    settings.watched = state.settings.lock().unwrap().watched.clone();
    settings.save(&state.settings_file())?;
    *state.settings.lock().unwrap() = settings.clone();
    apply_tray_locale(&app, &settings.locale);
    Ok(settings)
}

// ───────────────────────────── sidecar ─────────────────────────────

#[derive(Serialize, Clone)]
struct CliLine {
    stream: &'static str,
    line: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct CliResult {
    code: Option<i32>,
    ok: bool,
    timed_out: bool,
    lines: Vec<String>,
}

fn child_path_env() -> String {
    let home = std::env::var("HOME").unwrap_or_default();
    let mut parts: Vec<String> = vec![
        "/opt/homebrew/bin".into(),
        "/usr/local/bin".into(),
        format!("{home}/.orbstack/bin"),
        format!("{home}/.docker/bin"),
        format!("{home}/.local/bin"),
        "/usr/bin".into(),
        "/bin".into(),
        "/usr/sbin".into(),
        "/sbin".into(),
    ];
    if let Ok(p) = std::env::var("PATH") {
        parts.push(p);
    }
    parts.join(":")
}

#[tauri::command]
async fn cli_run(app: AppHandle, state: State<'_, AppState>, call: CliCall) -> Res<CliResult> {
    let plan = sidecar::plan(&call)?;
    let bin = sidecar_path().ok_or("the orazaka CLI sidecar is missing from this build")?;
    {
        let mut busy = state.busy.lock().unwrap();
        if *busy {
            return Err("another command is still running".into());
        }
        *busy = true;
    }
    let cwd = state.settings.lock().unwrap().workspace_dir.clone();
    let app2 = app.clone();
    let result = tauri::async_runtime::spawn_blocking(move || run_sidecar(&app2, &bin, &plan, cwd))
        .await
        .map_err(|e| e.to_string())?;
    *state.busy.lock().unwrap() = false;
    result
}

fn run_sidecar(
    app: &AppHandle,
    bin: &Path,
    plan: &sidecar::Plan,
    cwd: Option<String>,
) -> Res<CliResult> {
    let mut cmd = Command::new(bin);
    cmd.args(&plan.args)
        .env("PATH", child_path_env())
        .env("NO_COLOR", "1")
        .env("FORCE_COLOR", "0")
        .env("CI", "1")
        .env("ORAZAKA_SKIP_VERSION_CHECK", "1")
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    if let Some(dir) = cwd.filter(|d| Path::new(d).is_dir()) {
        cmd.current_dir(dir);
    }
    let mut child = cmd
        .spawn()
        .map_err(|e| format!("cannot start the CLI: {e}"))?;
    let lines = Arc::new(Mutex::new(Vec::<String>::new()));
    let mut readers = Vec::new();
    for (stream, pipe) in [
        (
            "stdout",
            child
                .stdout
                .take()
                .map(|p| Box::new(p) as Box<dyn std::io::Read + Send>),
        ),
        (
            "stderr",
            child
                .stderr
                .take()
                .map(|p| Box::new(p) as Box<dyn std::io::Read + Send>),
        ),
    ] {
        let Some(pipe) = pipe else { continue };
        let (app, lines) = (app.clone(), lines.clone());
        readers.push(std::thread::spawn(move || {
            for line in BufReader::new(pipe).lines().map_while(Result::ok) {
                let _ = app.emit(
                    "cli-line",
                    CliLine {
                        stream,
                        line: line.clone(),
                    },
                );
                let mut l = lines.lock().unwrap();
                if l.len() < 5000 {
                    l.push(line);
                }
            }
        }));
    }
    let deadline = Instant::now() + Duration::from_secs(plan.timeout_secs);
    let (mut code, mut timed_out) = (None, false);
    loop {
        match child.try_wait() {
            Ok(Some(status)) => {
                code = status.code();
                break;
            }
            Ok(None) if Instant::now() > deadline => {
                let _ = child.kill();
                let _ = child.wait();
                timed_out = true;
                break;
            }
            Ok(None) => std::thread::sleep(Duration::from_millis(150)),
            Err(e) => return Err(e.to_string()),
        }
    }
    for r in readers {
        let _ = r.join();
    }
    let lines = lines.lock().unwrap().clone();
    Ok(CliResult {
        ok: code == Some(0),
        code,
        timed_out,
        lines,
    })
}

// ───────────────────────────── file access ─────────────────────────────

fn resolve_readable(state: &AppState, path: &str) -> Res<ScopedPath> {
    let p = Path::new(path);
    if let Ok(ok) = state.scope.lock().unwrap().resolve(p) {
        return Ok(ok);
    }
    // A file the user dropped on the window this session is an explicit, one-file permission: the kit's own gate for it.
    if let Ok(canon) = p.canonicalize() {
        if state.drops.lock().unwrap().files.contains(&canon) {
            let mut dropped = Access::default();
            dropped.allow_dropped(&canon);
            if let Ok(ok) = dropped.resolve(p) {
                return Ok(ok);
            }
        }
    }
    Err(format!("outside the granted folders: {path}"))
}

#[tauri::command]
async fn pick_folder(app: AppHandle, state: State<'_, AppState>) -> Res<Option<String>> {
    let app2 = app.clone();
    let picked =
        tauri::async_runtime::spawn_blocking(move || app2.dialog().file().blocking_pick_folder())
            .await
            .map_err(|e| e.to_string())?;
    let Some(picked) = picked else {
        return Ok(None);
    };
    let path = picked.into_path().map_err(|e| e.to_string())?;
    grant_path(&state, &path).map(Some)
}

fn grant_path(state: &AppState, path: &Path) -> Res<String> {
    let mut scope = state.scope.lock().unwrap();
    let canon = scope.grant(path).map_err(|e| e.to_string())?;
    state.persist_scope(&scope)?;
    Ok(canon.display().to_string())
}

/// Grants a folder the user dropped on the window — but only that one, and only after the UI asked.
#[tauri::command]
fn grant_dropped(state: State<'_, AppState>, path: String) -> Res<String> {
    let canon = Path::new(&path).canonicalize().map_err(|e| e.to_string())?;
    if !state.drops.lock().unwrap().folders.contains(&canon) {
        return Err("this folder was not dropped on the window".into());
    }
    grant_path(&state, &canon)
}

#[tauri::command]
fn revoke_folder(state: State<'_, AppState>, path: String) -> Res<Vec<String>> {
    let root = PathBuf::from(&path);
    let mut scope = state.scope.lock().unwrap();
    scope.revoke(&root);
    state.persist_scope(&scope)?;
    stop_watchers_outside(&state, &scope);
    state.automations.revoke_grants_under(&root);
    drop_session_files_under(&state, &root);
    Ok(scope
        .roots()
        .iter()
        .map(|p| p.display().to_string())
        .collect())
}

#[tauri::command]
fn revoke_all(state: State<'_, AppState>) -> Res<()> {
    let mut scope = state.scope.lock().unwrap();
    scope.revoke_all();
    state.persist_scope(&scope)?;
    state.automations.revoke_grants_under(Path::new("/"));
    state.watchers.lock().unwrap().clear();
    let mut s = state.settings.lock().unwrap();
    s.watched.clear();
    s.save(&state.settings_file())?;
    let mut d = state.drops.lock().unwrap();
    d.files.clear();
    d.folders.clear();
    Ok(())
}

fn drop_session_files_under(state: &AppState, root: &Path) {
    state
        .drops
        .lock()
        .unwrap()
        .files
        .retain(|f| !f.starts_with(root));
}

fn stop_watchers_outside(state: &AppState, scope: &ScopeStore) {
    let mut w = state.watchers.lock().unwrap();
    w.retain(|path, _| scope.roots().iter().any(|r| path.starts_with(r)));
    let mut s = state.settings.lock().unwrap();
    s.watched
        .retain(|path| scope.roots().iter().any(|r| path.starts_with(r)));
    let _ = s.save(&state.settings_file());
}

#[tauri::command]
fn list_dir(state: State<'_, AppState>, path: String) -> Res<Listing> {
    let dir = state
        .scope
        .lock()
        .unwrap()
        .resolve(Path::new(&path))
        .map_err(|e| e.to_string())?;
    let limits = state.settings.lock().unwrap().limits.to_kit();
    files::list_dir(&dir, &limits).map_err(|e| e.to_string())
}

/// A preview reads a whole file (up to the settings' limits): off the main thread, so the window never waits for the disk.
#[tauri::command]
async fn preview_file(app: AppHandle, path: String) -> Res<Preview> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<AppState>();
        let limits = state.settings.lock().unwrap().limits.to_kit();
        let file = resolve_readable(&state, &path)?;
        files::preview(&file, &limits).map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
fn reveal_in_finder(state: State<'_, AppState>, path: String) -> Res<()> {
    let p = resolve_readable(&state, &path)?;
    Command::new("open")
        .arg("-R")
        .arg(p.as_path())
        .spawn()
        .map(|_| ())
        .map_err(|e| e.to_string())
}

// ───────────────────────────── folder watch ─────────────────────────────

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct FileAdded {
    root: String,
    entry: Entry,
}

#[tauri::command]
fn watch_set(
    app: AppHandle,
    state: State<'_, AppState>,
    path: String,
    enabled: bool,
) -> Res<Vec<String>> {
    let dir = state
        .scope
        .lock()
        .unwrap()
        .resolve(Path::new(&path))
        .map(ScopedPath::into_path_buf)
        .map_err(|e| e.to_string())?;
    if enabled {
        start_watch(&app, &state, &dir)?;
    } else {
        state.watchers.lock().unwrap().remove(&dir);
    }
    let mut s = state.settings.lock().unwrap();
    s.watched.retain(|p| p != &dir);
    if enabled {
        s.watched.push(dir);
    }
    s.save(&state.settings_file())?;
    Ok(s.watched.iter().map(|p| p.display().to_string()).collect())
}

fn start_watch(app: &AppHandle, state: &AppState, dir: &Path) -> Res<()> {
    let mut watchers = state.watchers.lock().unwrap();
    if watchers.contains_key(dir) {
        return Ok(());
    }
    let (app2, root) = (app.clone(), dir.to_path_buf());
    let seen: Arc<Mutex<HashMap<PathBuf, Instant>>> = Arc::new(Mutex::new(HashMap::new()));
    let mut watcher = notify::recommended_watcher(move |res: notify::Result<notify::Event>| {
        let Ok(ev) = res else { return };
        let interesting = matches!(
            ev.kind,
            EventKind::Create(_) | EventKind::Modify(notify::event::ModifyKind::Name(_))
        );
        if !interesting {
            return;
        }
        for path in ev.paths {
            let name = path
                .file_name()
                .map(|n| n.to_string_lossy().to_string())
                .unwrap_or_default();
            if files::is_noise(&name) {
                continue;
            }
            {
                let mut s = seen.lock().unwrap();
                if s.get(&path)
                    .is_some_and(|t| t.elapsed() < Duration::from_secs(5))
                {
                    continue;
                }
                s.insert(path.clone(), Instant::now());
            }
            let (app, root) = (app2.clone(), root.clone());
            std::thread::spawn(move || {
                // Wait for the copy to finish: report the file once its size stops changing.
                let size = |p: &Path| std::fs::metadata(p).map(|m| (m.is_file(), m.len())).ok();
                let Some((true, first)) = size(&path) else {
                    return;
                };
                std::thread::sleep(Duration::from_millis(700));
                let Some((true, second)) = size(&path) else {
                    return;
                };
                if first != second {
                    std::thread::sleep(Duration::from_millis(1500));
                }
                let Ok(meta) = std::fs::metadata(&path) else {
                    return;
                };
                let name = path
                    .file_name()
                    .map(|n| n.to_string_lossy().to_string())
                    .unwrap_or_default();
                let modified = meta
                    .modified()
                    .ok()
                    .and_then(|m| m.duration_since(std::time::UNIX_EPOCH).ok())
                    .map(|d| d.as_secs())
                    .unwrap_or(0);
                let _ = app.emit(
                    "file-added",
                    FileAdded {
                        root: root.display().to_string(),
                        entry: Entry {
                            kind: files::kind_of(&name),
                            path: path.display().to_string(),
                            is_dir: false,
                            size: meta.len(),
                            modified,
                            name,
                        },
                    },
                );
            });
        }
    })
    .map_err(|e| e.to_string())?;
    watcher
        .watch(dir, RecursiveMode::NonRecursive)
        .map_err(|e| e.to_string())?;
    watchers.insert(dir.to_path_buf(), watcher);
    Ok(())
}

// ───────────────────────────── automations (ADR-079) ─────────────────────────────

/// The real tools, behind the folder scope: an automation whose folder was revoked cannot start its MCP server.
struct ScopedBackends {
    scope: Arc<Mutex<ScopeStore>>,
}

impl automation::Backends for ScopedBackends {
    fn fs(&self, root: &Path) -> Result<Box<dyn automation::mcp::FsTools>, String> {
        let resolved = self
            .scope
            .lock()
            .unwrap()
            .resolve(root)
            .map_err(|e| format!("the folder is no longer granted: {e}"))?;
        automation::RealBackends.fs(resolved.as_path())
    }
    fn model(
        &self,
        target: &automation::types::ModelTarget,
    ) -> Box<dyn automation::model::ModelClient> {
        automation::RealBackends.model(target)
    }
}

/// Every 20 s: expire stale approvals, start what is due (each run on its own thread).
fn spawn_scheduler(svc: Arc<automation::Service>) {
    std::thread::spawn(move || loop {
        for id in svc.due() {
            let svc = svc.clone();
            std::thread::spawn(move || {
                let _ = svc.run_now(&id, automation::types::RunTrigger::Schedule);
            });
        }
        std::thread::sleep(Duration::from_secs(20));
    });
}

// ---- Code on the Mac (ADR-093 slice 1) ---------------------------------------------------------------------------

fn code_repo(state: &State<'_, AppState>) -> Res<code::Repo> {
    state
        .code
        .lock()
        .unwrap()
        .clone()
        .ok_or_else(|| "no repository is open".to_string())
}

/// Opens a repository inside a folder the user granted with the native picker.
#[tauri::command]
fn code_open(state: State<'_, AppState>, path: String) -> Res<code::RepoInfo> {
    let canon = state
        .scope
        .lock()
        .unwrap()
        .resolve(Path::new(&path))
        .map(ScopedPath::into_path_buf)
        .map_err(|e| e.to_string())?;
    let repo = code::Repo::open(&canon, scope::home_dir().as_deref()).map_err(|e| e.to_string())?;
    let info = repo.info().map_err(|e| e.to_string())?;
    *state.code.lock().unwrap() = Some(repo);
    Ok(info)
}

#[tauri::command]
fn code_read_file(state: State<'_, AppState>, path: String) -> Res<code::tools::FileText> {
    code::tools::read_file(&code_repo(&state)?, &path).map_err(|e| e.to_string())
}

#[tauri::command]
fn code_list_dir(state: State<'_, AppState>, path: String) -> Res<Vec<code::tools::Entry>> {
    code::tools::list_dir(&code_repo(&state)?, &path).map_err(|e| e.to_string())
}

#[tauri::command]
fn code_search_text(state: State<'_, AppState>, query: String) -> Res<Vec<code::tools::Match>> {
    code::tools::search_text(&code_repo(&state)?, &query).map_err(|e| e.to_string())
}

#[tauri::command]
fn code_outline(state: State<'_, AppState>, path: String) -> Res<Vec<code::tools::Symbol>> {
    code::tools::outline(&code_repo(&state)?, &path).map_err(|e| e.to_string())
}

/// A proposed diff: computed, shown, never written.
#[tauri::command]
fn code_propose(
    state: State<'_, AppState>,
    path: String,
    new_text: String,
) -> Res<code::diff::FileProposal> {
    code::diff::propose(&code_repo(&state)?, &path, &new_text).map_err(|e| e.to_string())
}

/// What the review screen sends back: one file and the hunks the user approved.
#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase")]
struct CodeDecision {
    file: code::diff::FileProposal,
    approved: Vec<usize>,
}

/// Applies the approved hunks (the only write path of the Code tab). A snapshot of the tree is taken first.
#[tauri::command]
fn code_apply(
    state: State<'_, AppState>,
    run_id: String,
    decisions: Vec<CodeDecision>,
) -> Res<Vec<code::diff::Applied>> {
    let repo = code_repo(&state)?;
    if decisions.iter().all(|d| d.approved.is_empty()) {
        return Ok(vec![]);
    }
    code::snapshot::create(&repo, &run_id).map_err(|e| e.to_string())?;
    let mut applied = Vec::new();
    for d in &decisions {
        // a hunk that changes what a check runs makes the next run ask again (computed here, not read from the page)
        let changes_what_runs = code::diff::approved_exec_surface(&repo, &d.file, &d.approved);
        let one = code::diff::apply(&repo, &d.file, &d.approved).map_err(|e| e.to_string())?;
        if one.written && changes_what_runs {
            state.code_checks.lock().unwrap().session.note_applied(true);
        }
        applied.push(one);
    }
    Ok(applied)
}

// ---- checks: the only `exec` (ADR-093 slice 2) ----------------------------------------------------------------

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ChecksOverview {
    /// Proposed by the repository; nothing here can run.
    suggested: Vec<code::checks::Check>,
    /// Confirmed by the user on this device for this repository.
    confirmed: Vec<code::checks::Grant>,
    /// The next run needs the user's approval.
    needs_approval: bool,
}

fn code_stamp() -> String {
    chrono::Local::now().format("%Y-%m-%dT%H:%M:%S").to_string()
}

fn code_audit(state: &State<'_, AppState>, event: &str, data: serde_json::Value) {
    state.automations.audit_event(event, data);
}

#[tauri::command]
fn code_checks_overview(state: State<'_, AppState>) -> Res<ChecksOverview> {
    let repo = code_repo(&state)?;
    let book = state.code_checks.lock().unwrap();
    Ok(ChecksOverview {
        suggested: code::checks::suggest(&repo),
        confirmed: book.list(&repo).into_iter().cloned().collect(),
        needs_approval: book.session.needs_approval(),
    })
}

/// The user confirmed a check in the app (an `exec` grant: listed, revocable, audited).
#[tauri::command]
fn code_check_confirm(
    state: State<'_, AppState>,
    check: code::checks::Check,
) -> Res<code::checks::Grant> {
    let repo = code_repo(&state)?;
    let grant = state
        .code_checks
        .lock()
        .unwrap()
        .confirm(&repo, check, &code_stamp())
        .map_err(|e| e.to_string())?;
    code_audit(
        &state,
        "code.check.confirmed",
        serde_json::json!({ "grant": grant.id, "repo": grant.repo, "name": grant.check.name, "argvHash": grant.argv_hash, "cwd": grant.check.cwd, "network": grant.check.network }),
    );
    Ok(grant)
}

#[tauri::command]
fn code_check_revoke(state: State<'_, AppState>, id: String) -> Res<bool> {
    let revoked = state.code_checks.lock().unwrap().revoke(&id, &code_stamp());
    if revoked {
        code_audit(
            &state,
            "code.check.revoked",
            serde_json::json!({ "grant": id }),
        );
    }
    Ok(revoked)
}

/// Runs a confirmed check by name. `approved` is the user's approval of this very run.
#[tauri::command]
async fn code_check_run(
    state: State<'_, AppState>,
    name: String,
    approved: bool,
) -> Res<code::checks::CheckRun> {
    let repo = code_repo(&state)?;
    let result = code::checks::run(
        &repo,
        &mut state.code_checks.lock().unwrap(),
        &name,
        approved,
        &state.code_rules.limits(),
    );
    match result {
        Ok(run) => {
            code_audit(&state, "code.check.run", run.audit_data());
            Ok(run)
        }
        Err(e) => {
            code_audit(
                &state,
                "code.check.refused",
                serde_json::json!({ "name": name, "reason": e.to_string() }),
            );
            Err(e.to_string())
        }
    }
}

/// GitHub (slice 4): what the page may know. Never a token.
#[tauri::command]
fn github_status(state: State<'_, AppState>) -> Res<code::github::GithubStatus> {
    state.github.status(&*state.secrets)
}

/// Starts the device flow: returns the code to type and where; the wait runs on its own thread.
#[tauri::command]
async fn github_sign_in(state: State<'_, AppState>) -> Res<krizaka_rust::device_flow::DeviceCode> {
    let spawn = |job: Box<dyn FnOnce() + Send>| {
        std::thread::spawn(job);
    };
    let code = state.github.begin(
        Arc::new(code::github::HttpTransport),
        state.secrets.clone(),
        &state.github_config,
        spawn,
    )?;
    code_audit(
        &state,
        "code.github.sign_in",
        serde_json::json!({ "userCode": code.user_code }),
    );
    Ok(code)
}

/// Signs out here: both Keychain items go. Revoking the App is done in GitHub's settings.
#[tauri::command]
fn github_sign_out(state: State<'_, AppState>) -> Res<()> {
    state.github.sign_out(&*state.secrets)?;
    code_audit(&state, "code.github.sign_out", serde_json::json!({}));
    Ok(())
}

/// Undo: puts the touched files back from `refs/orazaka/snapshots/<run-id>`.
#[tauri::command]
fn code_undo(state: State<'_, AppState>, run_id: String, paths: Vec<String>) -> Res<Vec<String>> {
    code::snapshot::restore(&code_repo(&state)?, &run_id, &paths).map_err(|e| e.to_string())
}

#[tauri::command]
fn code_open_in_editor(
    state: State<'_, AppState>,
    editor: code::editor::Editor,
    path: String,
    line: usize,
) -> Res<()> {
    code::editor::open(&code_repo(&state)?, editor, &path, line).map_err(|e| e.to_string())
}

#[tauri::command]
fn automations_overview(state: State<'_, AppState>) -> automation::Overview {
    state.automations.overview()
}

#[tauri::command]
fn automation_create(
    state: State<'_, AppState>,
    input: automation::NewAutomation,
) -> Res<automation::types::Automation> {
    let scope = state.scope.clone();
    state.automations.create(input, |p| {
        scope
            .lock()
            .unwrap()
            .resolve(p)
            .map(ScopedPath::into_path_buf)
            .map_err(|e| e.to_string())
    })
}

#[tauri::command]
fn automation_set_enabled(state: State<'_, AppState>, id: String, enabled: bool) -> Res<()> {
    state.automations.set_enabled(&id, enabled)
}

#[tauri::command]
fn automation_set_dry_run(state: State<'_, AppState>, id: String, dry_run: bool) -> Res<()> {
    state.automations.set_dry_run(&id, dry_run)
}

#[tauri::command]
fn automation_delete(state: State<'_, AppState>, id: String) -> Res<()> {
    state.automations.delete(&id)
}

#[tauri::command]
async fn automation_run_now(state: State<'_, AppState>, id: String) -> Res<String> {
    let svc = state.automations.clone();
    tauri::async_runtime::spawn_blocking(move || {
        svc.run_now(&id, automation::types::RunTrigger::Manual)
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
fn automation_run(state: State<'_, AppState>, id: String) -> Res<automation::types::Run> {
    state
        .automations
        .run(&id)
        .ok_or_else(|| "unknown run".into())
}

#[tauri::command]
async fn approval_decide(
    state: State<'_, AppState>,
    id: String,
    decision: automation::types::Decision,
) -> Res<automation::RunSummary> {
    let svc = state.automations.clone();
    tauri::async_runtime::spawn_blocking(move || svc.decide(&id, decision))
        .await
        .map_err(|e| e.to_string())?
}

#[tauri::command]
fn grant_revoke(state: State<'_, AppState>, id: String) -> Res<()> {
    state.automations.revoke_grant(&id)
}

#[tauri::command]
fn kill_switch_set(state: State<'_, AppState>, halted: bool) {
    state.automations.set_halted(halted);
}

#[tauri::command]
fn audit_log(state: State<'_, AppState>, limit: Option<usize>) -> automation::AuditView {
    state.automations.audit_view(limit.unwrap_or(200).min(1000))
}

#[tauri::command]
async fn models_list(base_url: String) -> Res<Vec<String>> {
    tauri::async_runtime::spawn_blocking(move || {
        automation::model::OpenAiCompatible::list_models(&base_url, Duration::from_secs(5))
    })
    .await
    .map_err(|e| e.to_string())?
}

// ───────────────────────────── workspace window ─────────────────────────────

#[tauri::command]
fn open_workspace(app: AppHandle, state: State<'_, AppState>) -> Res<String> {
    let url = state.settings.lock().unwrap().workspace_url();
    *state.workspace_origin.lock().unwrap() = url.clone();
    let parsed = url::Url::parse(&url).map_err(|e| e.to_string())?;
    if let Some(w) = app.get_webview_window("workspace") {
        w.navigate(parsed).map_err(|e| e.to_string())?;
        let _ = w.show();
        let _ = w.set_focus();
        return Ok(url);
    }
    let origin = state.workspace_origin.clone();
    // No capability is bound to this window's label: the hosted page gets no IPC at all. Its file drops
    // are the browser's own (the OS drag-drop handler is off), so a PDF can go straight into a Studio.
    WebviewWindowBuilder::new(&app, "workspace", WebviewUrl::External(parsed))
        .title("Sovkora")
        .inner_size(1280.0, 860.0)
        .min_inner_size(900.0, 600.0)
        .disable_drag_drop_handler()
        .on_navigation(move |target| {
            let allowed = origin.lock().unwrap().clone();
            if settings::same_origin(&allowed, target) {
                return true;
            }
            if matches!(target.scheme(), "http" | "https") {
                let _ = Command::new("open").arg(target.as_str()).spawn();
            }
            false
        })
        .build()
        .map_err(|e| e.to_string())?;
    Ok(url)
}

/// Reads a granted file and puts it in the first file field of the open workspace page, as if it
/// had been chosen there (Studio input, knowledge-base upload).
#[tauri::command]
async fn workspace_send_file(app: AppHandle, path: String) -> Res<()> {
    // The read and the base64 of a file up to the send limit: off the main thread.
    tauri::async_runtime::spawn_blocking(move || send_file(&app, &path))
        .await
        .map_err(|e| e.to_string())?
}

fn send_file(app: &AppHandle, path: &str) -> Res<()> {
    use base64::{engine::general_purpose::STANDARD, Engine};
    use std::io::Read;
    let state = app.state::<AppState>();
    let max_send = state.settings.lock().unwrap().limits.to_kit().send();
    let scoped = resolve_readable(&state, path)?;
    // One descriptor: the size is checked on the file that is read, never on a path that may have changed.
    let opened = scoped.open().map_err(|e| e.to_string())?;
    let meta = opened.metadata().map_err(|e| e.to_string())?;
    if !meta.is_file() || meta.len() > max_send {
        return Err(format!(
            "not a file, or larger than {} MB",
            max_send / (1024 * 1024)
        ));
    }
    let file = scoped.as_path();
    let window = app
        .get_webview_window("workspace")
        .ok_or("open the workspace first")?;
    let name = file
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_default();
    let mut bytes = Vec::new();
    opened
        .take(max_send.saturating_add(1))
        .read_to_end(&mut bytes)
        .map_err(|e| e.to_string())?;
    if bytes.len() as u64 > max_send {
        return Err("the file grew past the limit while it was read".into());
    }
    let data = STANDARD.encode(bytes);
    let js = format!(
        r#"(()=>{{
  const bin=atob("{data}"),u=new Uint8Array(bin.length);
  for(let i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);
  const f=new File([u],{name},{{type:{mime}}}),dt=new DataTransfer();dt.items.add(f);
  const input=[...document.querySelectorAll('input[type=file]')].find(i=>!i.disabled);
  if(!input)return;
  input.files=dt.files;
  input.dispatchEvent(new Event('input',{{bubbles:true}}));
  input.dispatchEvent(new Event('change',{{bubbles:true}}));
}})();"#,
        name = serde_json::to_string(&name).unwrap(),
        mime = serde_json::to_string(files::mime_of(&name)).unwrap(),
    );
    window.eval(&js).map_err(|e| e.to_string())?;
    let _ = window.show();
    let _ = window.set_focus();
    Ok(())
}

// ───────────────────────────── autostart, tray ─────────────────────────────

#[tauri::command]
fn autostart_set(app: AppHandle, enabled: bool) -> Res<bool> {
    let m = app.autolaunch();
    if enabled { m.enable() } else { m.disable() }.map_err(|e| e.to_string())?;
    Ok(m.is_enabled().unwrap_or(enabled))
}

fn tray_text(locale: &str, key: &str) -> &'static str {
    match (locale, key) {
        ("fr", "panel") => "Ouvrir le panneau de contrôle",
        ("fr", "workspace") => "Ouvrir l'espace de travail",
        ("fr", "start") => "Démarrer la pile locale",
        ("fr", "stop") => "Arrêter la pile locale",
        ("fr", "quit") => "Quitter Sovkora",
        (_, "panel") => "Open control panel",
        (_, "workspace") => "Open workspace",
        (_, "start") => "Start local stack",
        (_, "stop") => "Stop local stack",
        _ => "Quit Sovkora",
    }
}

fn tray_status_text(locale: &str, state: &str) -> String {
    let word = match (locale, state) {
        ("fr", "running") => "en marche",
        ("fr", "partial") => "partielle",
        ("fr", _) => "arrêtée",
        (_, "running") => "running",
        (_, "partial") => "partial",
        _ => "stopped",
    };
    if locale == "fr" {
        format!("Pile locale : {word}")
    } else {
        format!("Local stack: {word}")
    }
}

fn apply_tray_locale(app: &AppHandle, locale: &str) {
    let state = app.state::<AppState>();
    if let Some(t) = state.tray.lock().unwrap().as_ref() {
        let _ = t.panel.set_text(tray_text(locale, "panel"));
        let _ = t.workspace.set_text(tray_text(locale, "workspace"));
        let _ = t.start.set_text(tray_text(locale, "start"));
        let _ = t.stop.set_text(tray_text(locale, "stop"));
        let _ = t.quit.set_text(tray_text(locale, "quit"));
        let last = state.last_probe.lock().unwrap().clone();
        let _ = t.status.set_text(tray_status_text(locale, &last));
    };
}

fn show_panel(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.show();
        let _ = w.unminimize();
        let _ = w.set_focus();
    }
}

fn build_tray(app: &AppHandle) -> tauri::Result<()> {
    let locale = app
        .state::<AppState>()
        .settings
        .lock()
        .unwrap()
        .locale
        .clone();
    let status = MenuItem::with_id(
        app,
        "status",
        tray_status_text(&locale, "stopped"),
        false,
        None::<&str>,
    )?;
    let panel = MenuItem::with_id(
        app,
        "panel",
        tray_text(&locale, "panel"),
        true,
        None::<&str>,
    )?;
    let workspace = MenuItem::with_id(
        app,
        "workspace",
        tray_text(&locale, "workspace"),
        true,
        None::<&str>,
    )?;
    let start = MenuItem::with_id(
        app,
        "start",
        tray_text(&locale, "start"),
        true,
        None::<&str>,
    )?;
    let stop = MenuItem::with_id(app, "stop", tray_text(&locale, "stop"), true, None::<&str>)?;
    let quit = MenuItem::with_id(
        app,
        "quit",
        tray_text(&locale, "quit"),
        true,
        Some("CmdOrCtrl+Q"),
    )?;
    let sep1 = PredefinedMenuItem::separator(app)?;
    let sep2 = PredefinedMenuItem::separator(app)?;
    let menu = Menu::with_items(
        app,
        &[
            &status, &sep1, &panel, &workspace, &start, &stop, &sep2, &quit,
        ],
    )?;
    TrayIconBuilder::with_id("main")
        .icon(tauri::image::Image::from_bytes(TRAY_OFF)?)
        .icon_as_template(true)
        .tooltip("Sovkora")
        .menu(&menu)
        .on_menu_event(|app, event| match event.id.as_ref() {
            "panel" => show_panel(app),
            "workspace" => {
                let _ = open_workspace(app.clone(), app.state::<AppState>());
            }
            "start" | "stop" => {
                show_panel(app);
                let _ = app.emit("tray-action", event.id.as_ref().to_string());
            }
            "quit" => app.exit(0),
            _ => {}
        })
        .build(app)?;
    *app.state::<AppState>().tray.lock().unwrap() = Some(TrayItems {
        status,
        panel,
        workspace,
        start,
        stop,
        quit,
    });
    Ok(())
}

fn spawn_probe_loop(app: AppHandle) {
    std::thread::spawn(move || {
        let mut previous = String::new();
        loop {
            let p = probe();
            if p.state != previous {
                previous = p.state.clone();
                let state = app.state::<AppState>();
                *state.last_probe.lock().unwrap() = p.state.clone();
                let locale = state.settings.lock().unwrap().locale.clone();
                if let Some(t) = state.tray.lock().unwrap().as_ref() {
                    let _ = t.status.set_text(tray_status_text(&locale, &p.state));
                }
                if let Some(tray) = app.tray_by_id("main") {
                    let bytes = if p.state == "stopped" {
                        TRAY_OFF
                    } else {
                        TRAY_ON
                    };
                    if let Ok(img) = tauri::image::Image::from_bytes(bytes) {
                        let _ = tray.set_icon(Some(img));
                        let _ = tray.set_icon_as_template(true);
                    }
                    let _ = tray.set_tooltip(Some(format!("Sovkora — {}", p.state)));
                }
                let _ = app.emit("stack-probe", &p);
            }
            std::thread::sleep(Duration::from_secs(8));
        }
    });
}

// ───────────────────────────── drops on the control panel ─────────────────────────────

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct Dropped {
    files: Vec<Entry>,
    folders: Vec<String>,
}

fn on_panel_drop(app: &AppHandle, paths: &[PathBuf]) {
    let state = app.state::<AppState>();
    let mut out = Dropped {
        files: vec![],
        folders: vec![],
    };
    let mut drops = state.drops.lock().unwrap();
    for p in paths {
        let Ok(canon) = p.canonicalize() else {
            continue;
        };
        let Ok(meta) = std::fs::metadata(&canon) else {
            continue;
        };
        if meta.is_dir() {
            if !drops.folders.contains(&canon) {
                drops.folders.push(canon.clone());
            }
            out.folders.push(canon.display().to_string());
        } else if meta.is_file() {
            if !drops.files.contains(&canon) {
                drops.files.push(canon.clone());
            }
            let name = canon
                .file_name()
                .map(|n| n.to_string_lossy().to_string())
                .unwrap_or_default();
            out.files.push(Entry {
                kind: files::kind_of(&name),
                path: canon.display().to_string(),
                is_dir: false,
                size: meta.len(),
                modified: 0,
                name,
            });
        }
    }
    drop(drops);
    let _ = app.emit("dropped", out);
}

// ───────────────────────────── run ─────────────────────────────

pub fn run() {
    let builder = tauri::Builder::default();
    // The Mac journey's embedded WebDriver (127.0.0.1:4445): a debug build with `--features webdriver`, never a release.
    #[cfg(all(debug_assertions, feature = "webdriver"))]
    let builder = builder.plugin(tauri_plugin_webdriver::init());
    let app = builder
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_autostart::init(
            MacosLauncher::LaunchAgent,
            Some(vec!["--minimized"]),
        ))
        .setup(|app| {
            let data_dir = app.path().app_data_dir()?;
            let settings = Settings::load(&data_dir.join("settings.json"));
            let scope = Arc::new(Mutex::new(ScopeStore::load(&data_dir.join("scope.json"))));
            let watched = settings.watched.clone();
            let emitter = app.handle().clone();
            let automations = automation::Service::open(
                &data_dir,
                Arc::new(ScopedBackends {
                    scope: scope.clone(),
                }),
                Arc::new(automation::now_local),
                Arc::new(move || {
                    let _ = emitter.emit("automations-changed", ());
                }),
            );
            spawn_scheduler(automations.clone());
            let checks_file = data_dir.join("code-checks.json");
            let github_file = data_dir.join("github.json");
            let code_rules = code::rules::CodeRules::load(&data_dir.join("code-rules.json"));
            app.manage(AppState {
                data_dir,
                settings: Mutex::new(settings),
                scope,
                automations,
                drops: Mutex::new(Drops::default()),
                watchers: Mutex::new(HashMap::new()),
                busy: Mutex::new(false),
                workspace_origin: Arc::new(Mutex::new(String::new())),
                tray: Mutex::new(None),
                last_probe: Mutex::new("stopped".into()),
                code: Mutex::new(None),
                code_checks: Mutex::new(code::checks::Book::open(&checks_file)),
                code_rules: code_rules.clone(),
                github: code::github::Connection::default(),
                github_config: code::github::GithubConfig::load(&github_file),
                secrets: Arc::new(krizaka_rust::secrets::Checked(
                    krizaka_rust::keychain::Keychain::new(&app.config().identifier),
                )),
            });
            let handle = app.handle().clone();
            let state = handle.state::<AppState>();
            // Re-arm the folder watches that are still granted.
            for dir in watched {
                if let Ok(resolved) = state.scope.lock().unwrap().resolve(&dir) {
                    let _ = start_watch(&handle, &state, resolved.as_path());
                }
            }
            build_tray(&handle)?;
            spawn_probe_loop(handle.clone());
            if std::env::args().any(|a| a == "--minimized") {
                if let Some(w) = handle.get_webview_window("main") {
                    let _ = w.hide();
                }
            }
            Ok(())
        })
        .on_window_event(|window, event| {
            if window.label() != "main" {
                return;
            }
            match event {
                // A menu-bar app: closing the panel hides it; "Quit Sovkora" in the tray quits.
                WindowEvent::CloseRequested { api, .. } => {
                    api.prevent_close();
                    let _ = window.hide();
                }
                WindowEvent::DragDrop(DragDropEvent::Drop { paths, .. }) => {
                    on_panel_drop(window.app_handle(), paths);
                }
                _ => {}
            }
        })
        .invoke_handler(tauri::generate_handler![
            get_snapshot,
            probe_stack,
            save_settings,
            cli_run,
            pick_folder,
            grant_dropped,
            revoke_folder,
            revoke_all,
            list_dir,
            preview_file,
            reveal_in_finder,
            watch_set,
            open_workspace,
            workspace_send_file,
            autostart_set,
            automations_overview,
            automation_create,
            automation_set_enabled,
            automation_set_dry_run,
            automation_delete,
            automation_run_now,
            automation_run,
            approval_decide,
            grant_revoke,
            kill_switch_set,
            audit_log,
            models_list,
            code_open,
            code_read_file,
            code_list_dir,
            code_search_text,
            code_outline,
            code_propose,
            code_apply,
            code_undo,
            code_open_in_editor,
            code_checks_overview,
            code_check_confirm,
            code_check_revoke,
            code_check_run,
            github_status,
            github_sign_in,
            github_sign_out,
        ])
        .build(tauri::generate_context!())
        .expect("error while building Sovkora Desktop");

    app.run(|handle, event| {
        if let RunEvent::Reopen { .. } = event {
            show_panel(handle);
        }
    });
}
