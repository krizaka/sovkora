//! Automations and local agents on this Mac — slice 1 of ADR-079.
//!
//! * `types`    — the model: automation = trigger + recipe + policy; a run is a job with a trace.
//! * `schedule` — when a slot is due, in local time, on the device.
//! * `store`    — the device's store (automations, runs, approvals, grants, kill switch, defaults rule set).
//! * `audit`    — the append-only, hash-chained audit log.
//! * `mcp`      — the MCP client, the catalogue of servers and the effect of each tool.
//! * `model`    — the OpenAI-compatible model port (Ollama, LocalAI, mlx_lm.server…).
//! * `runner`   — the fixed plan of `summarise-new-pdfs` and the write gate.
//!
//! This module knows nothing of Tauri: `Service` is driven by the app's commands and by a scheduler thread, and the
//! same core is what the headless runner (slice 6) will embed.

pub mod audit;
pub mod mcp;
pub mod model;
pub mod runner;
pub mod schedule;
pub mod store;
pub mod testpdf;
pub mod types;

use audit::{AuditEntry, AuditLog, Verification};
use mcp::{Catalogue, FsTools, McpFilesystem};
use model::{ModelClient, OpenAiCompatible};
use schedule::{format_day, format_slot, LocalDateTime};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::HashSet;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::Duration;
use store::{bounded, Defaults, Store};
use time::OffsetDateTime;
use types::*;

// @fixed: transport timeouts of the device's own processes, not business parameters.
const MCP_TIMEOUT: Duration = Duration::from_secs(120);
const MODEL_TIMEOUT: Duration = Duration::from_secs(600);

/// How the runner reaches its tools and its model — the real ones in the app, others in tests.
pub trait Backends: Send + Sync {
    fn fs(&self, root: &Path) -> Result<Box<dyn FsTools>, String>;
    fn model(&self, target: &ModelTarget) -> Box<dyn ModelClient>;
}

pub struct RealBackends;

impl Backends for RealBackends {
    fn fs(&self, root: &Path) -> Result<Box<dyn FsTools>, String> {
        Ok(Box::new(McpFilesystem::start(
            &Catalogue::bundled(),
            root,
            MCP_TIMEOUT,
        )?))
    }
    fn model(&self, target: &ModelTarget) -> Box<dyn ModelClient> {
        Box::new(OpenAiCompatible {
            base_url: target.base_url.clone(),
            model: target.model.clone(),
            timeout: MODEL_TIMEOUT,
        })
    }
}

/// What the "New automation" form sends.
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NewAutomation {
    pub name: String,
    pub folder: String,
    pub time: String,
    #[serde(default)]
    pub days: Vec<u8>,
    pub model: String,
    pub base_url: String,
    pub dry_run: bool,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AutomationView {
    #[serde(flatten)]
    pub automation: Automation,
    pub next_run: Option<String>,
    pub last_run: Option<RunSummary>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RunSummary {
    pub id: String,
    pub job_id: String,
    pub automation_id: String,
    pub automation_name: String,
    pub trigger: RunTrigger,
    pub status: RunStatus,
    pub dry_run: bool,
    pub started_at: String,
    pub finished_at: Option<String>,
    pub written: usize,
    pub steps: usize,
    pub usage: Usage,
}

impl From<&Run> for RunSummary {
    fn from(r: &Run) -> Self {
        Self {
            id: r.id.clone(),
            job_id: r.job_id.clone(),
            automation_id: r.automation_id.clone(),
            automation_name: r.automation_name.clone(),
            trigger: r.trigger,
            status: r.status,
            dry_run: r.dry_run,
            started_at: r.started_at.clone(),
            finished_at: r.finished_at.clone(),
            written: r.written.len(),
            steps: r.steps.len(),
            usage: r.usage.clone(),
        }
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Overview {
    pub automations: Vec<AutomationView>,
    pub runs: Vec<RunSummary>,
    pub approvals: Vec<Approval>,
    pub grants: Vec<Grant>,
    pub halted: bool,
    pub defaults: Defaults,
    /// Automations running right now.
    pub running: Vec<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AuditView {
    pub entries: Vec<AuditEntry>,
    pub verification: Verification,
}

/// The Mac's wall clock. If the OS cannot say its UTC offset (it can on macOS), UTC is the honest fallback.
pub fn now_local() -> LocalDateTime {
    let now = OffsetDateTime::now_local().unwrap_or_else(|_| OffsetDateTime::now_utc());
    LocalDateTime::new(now.date(), now.time())
}

fn stamp(t: LocalDateTime) -> String {
    format_slot(t)
}

fn new_id() -> String {
    uuid::Uuid::new_v4().to_string()
}

pub struct Service {
    store_file: PathBuf,
    pub store: Mutex<Store>,
    audit: Mutex<AuditLog>,
    active: Mutex<HashSet<String>>,
    backends: Arc<dyn Backends>,
    clock: Arc<dyn Fn() -> LocalDateTime + Send + Sync>,
    on_change: Arc<dyn Fn() + Send + Sync>,
}

impl Service {
    pub fn open(
        data_dir: &Path,
        backends: Arc<dyn Backends>,
        clock: Arc<dyn Fn() -> LocalDateTime + Send + Sync>,
        on_change: Arc<dyn Fn() + Send + Sync>,
    ) -> Arc<Self> {
        let store_file = data_dir.join("automations.json");
        let mut store = Store::load(&store_file);
        // A run the app was closed in the middle of did not finish: say so instead of showing it running forever.
        for r in store
            .runs
            .iter_mut()
            .filter(|r| r.status == RunStatus::Running)
        {
            r.status = RunStatus::Failed;
            r.error = Some("the app was closed during the run".into());
        }
        let _ = store.save(&store_file);
        Arc::new(Self {
            store_file,
            store: Mutex::new(store),
            audit: Mutex::new(AuditLog::open(&data_dir.join("audit.jsonl"))),
            active: Mutex::new(HashSet::new()),
            backends,
            clock,
            on_change,
        })
    }

    fn now(&self) -> LocalDateTime {
        (self.clock)()
    }

    fn persist(&self, store: &Store) {
        let _ = store.save(&self.store_file);
        (self.on_change)();
    }

    pub fn audit_event(&self, event: &str, data: Value) {
        let at = stamp(self.now());
        let _ = self.audit.lock().unwrap().append(&at, event, data);
    }

    pub fn overview(&self) -> Overview {
        let s = self.store.lock().unwrap();
        let now = self.now();
        Overview {
            automations: s
                .automations
                .iter()
                .map(|a| AutomationView {
                    automation: a.clone(),
                    next_run: (a.enabled && !s.halted)
                        .then(|| schedule::next_slot(&a.schedule, now))
                        .flatten()
                        .map(stamp),
                    last_run: s
                        .runs
                        .iter()
                        .find(|r| r.automation_id == a.id)
                        .map(RunSummary::from),
                })
                .collect(),
            runs: s.runs.iter().map(RunSummary::from).collect(),
            approvals: s.pending_approvals().into_iter().cloned().collect(),
            grants: s.active_grants().into_iter().cloned().collect(),
            halted: s.halted,
            defaults: s.defaults.clone(),
            running: self.active.lock().unwrap().iter().cloned().collect(),
        }
    }

    pub fn run(&self, id: &str) -> Option<Run> {
        self.store.lock().unwrap().run(id).cloned()
    }

    pub fn audit_view(&self, limit: usize) -> AuditView {
        let log = self.audit.lock().unwrap();
        AuditView {
            entries: log.tail(limit),
            verification: log.verify(),
        }
    }

    /// Creates an automation on a folder the user granted (`resolve` is the app's folder scope).
    pub fn create(
        &self,
        input: NewAutomation,
        resolve: impl Fn(&Path) -> Result<PathBuf, String>,
    ) -> Result<Automation, String> {
        let name = input.name.trim();
        if name.is_empty() || name.chars().count() > 80 {
            return Err("a name of 1 to 80 characters is required".into());
        }
        let folder = resolve(Path::new(&input.folder))?;
        if !folder.is_dir() {
            return Err("the folder must be a granted folder".into());
        }
        let sched = Schedule {
            time: input.time.clone(),
            days: input.days.clone(),
            catch_up: CatchUp::Once,
        };
        schedule::validate(&sched)?;
        OpenAiCompatible::validate_base_url(&input.base_url)?;
        if input.model.trim().is_empty() {
            return Err("a model is required".into());
        }
        let mut s = self.store.lock().unwrap();
        let a = Automation {
            id: new_id(),
            name: name.into(),
            recipe: Recipe::SummariseNewPdfs,
            schedule: sched,
            folder,
            model: ModelTarget {
                base_url: input.base_url.trim().into(),
                model: input.model.trim().into(),
            },
            policy: bounded(&s.defaults.policy),
            dry_run: input.dry_run,
            enabled: true,
            created_at: stamp(self.now()),
            last_slot: None,
        };
        s.automations.push(a.clone());
        self.audit_event(
            "automation.created",
            json!({ "automation": a.id, "recipe": a.recipe, "time": a.schedule.time, "days": a.schedule.days, "folder": a.folder, "model": a.model.model, "dryRun": a.dry_run }),
        );
        self.persist(&s);
        Ok(a)
    }

    pub fn set_enabled(&self, id: &str, enabled: bool) -> Result<(), String> {
        let mut s = self.store.lock().unwrap();
        let a = s.automation_mut(id).ok_or("unknown automation")?;
        a.enabled = enabled;
        self.audit_event(
            if enabled {
                "automation.resumed"
            } else {
                "automation.paused"
            },
            json!({ "automation": id }),
        );
        self.persist(&s);
        Ok(())
    }

    pub fn set_dry_run(&self, id: &str, dry_run: bool) -> Result<(), String> {
        let mut s = self.store.lock().unwrap();
        let a = s.automation_mut(id).ok_or("unknown automation")?;
        a.dry_run = dry_run;
        self.audit_event(
            "automation.edited",
            json!({ "automation": id, "dryRun": dry_run }),
        );
        self.persist(&s);
        Ok(())
    }

    pub fn delete(&self, id: &str) -> Result<(), String> {
        let mut s = self.store.lock().unwrap();
        let now = stamp(self.now());
        if !s.delete_automation(id, &now) {
            return Err("unknown automation".into());
        }
        self.audit_event("automation.deleted", json!({ "automation": id }));
        self.persist(&s);
        Ok(())
    }

    pub fn set_halted(&self, halted: bool) {
        let mut s = self.store.lock().unwrap();
        s.halted = halted;
        self.audit_event(
            if halted {
                "kill-switch.on"
            } else {
                "kill-switch.off"
            },
            json!({}),
        );
        self.persist(&s);
    }

    pub fn revoke_grant(&self, id: &str) -> Result<(), String> {
        let mut s = self.store.lock().unwrap();
        let now = stamp(self.now());
        let g = s
            .grants
            .iter_mut()
            .find(|g| g.id == id && g.revoked_at.is_none())
            .ok_or("unknown grant")?;
        g.revoked_at = Some(now);
        self.audit_event("grant.revoked", json!({ "grant": id }));
        self.persist(&s);
        Ok(())
    }

    /// Revokes every grant on a folder (and under it) — called when the user revokes the folder itself.
    pub fn revoke_grants_under(&self, folder: &Path) {
        let mut s = self.store.lock().unwrap();
        let now = stamp(self.now());
        let mut revoked = vec![];
        for g in s
            .grants
            .iter_mut()
            .filter(|g| g.revoked_at.is_none() && g.scope.starts_with(folder))
        {
            g.revoked_at = Some(now.clone());
            revoked.push(g.id.clone());
        }
        if !revoked.is_empty() {
            self.audit_event(
                "grant.revoked",
                json!({ "grants": revoked, "reason": "folder revoked" }),
            );
            self.persist(&s);
        }
    }

    /// Runs an automation now, synchronously (the caller picks the thread). Returns the run id.
    pub fn run_now(&self, id: &str, trigger: RunTrigger) -> Result<String, String> {
        if !self.active.lock().unwrap().insert(id.to_string()) {
            return Err("this automation is already running".into());
        }
        (self.on_change)();
        let result = self.run_now_inner(id, trigger);
        self.active.lock().unwrap().remove(id);
        (self.on_change)();
        result
    }

    fn run_now_inner(&self, id: &str, trigger: RunTrigger) -> Result<String, String> {
        let (automation, grants, halted, runs_today) = {
            let s = self.store.lock().unwrap();
            let a = s.automation(id).cloned().ok_or("unknown automation")?;
            let day = format_day(self.now());
            (a, s.grants.clone(), s.halted, s.runs_on(id, &day))
        };
        let catalogue = Catalogue::bundled();
        let model = self.backends.model(&automation.model);
        let halted_now = || halted || self.store.lock().unwrap().halted;
        let clock = self.clock.clone();
        let now = move || clock();
        let mut audit = |event: &str, data: Value| self.audit_event(event, data);
        let id_fn = new_id;

        let mut fs = match self.backends.fs(&automation.folder) {
            Ok(fs) => fs,
            Err(e) => {
                let mut env_fs = FailingFs(e.clone());
                let mut env = runner::Env {
                    fs: &mut env_fs,
                    model: model.as_ref(),
                    catalogue: &catalogue,
                    grants: &grants,
                    halted: &halted_now,
                    now: &now,
                    audit: &mut audit,
                    runs_today,
                    new_id: &id_fn,
                };
                let (run, _) = runner::execute(&automation, trigger, &mut env);
                return Ok(self.record(run, None));
            }
        };
        let mut env = runner::Env {
            fs: fs.as_mut(),
            model: model.as_ref(),
            catalogue: &catalogue,
            grants: &grants,
            halted: &halted_now,
            now: &now,
            audit: &mut audit,
            runs_today,
            new_id: &id_fn,
        };
        let (run, approval) = runner::execute(&automation, trigger, &mut env);
        Ok(self.record(run, approval))
    }

    fn record(&self, run: Run, approval: Option<Approval>) -> String {
        let mut s = self.store.lock().unwrap();
        let id = run.id.clone();
        if let Some(a) = approval {
            s.approvals.push(a);
        }
        s.upsert_run(run);
        self.persist(&s);
        id
    }

    /// The user's answer to an approval: performs the approved writes (through MCP), or cancels the run.
    pub fn decide(&self, approval_id: &str, decision: Decision) -> Result<RunSummary, String> {
        let (automation, run, mut approval, halted) = {
            let s = self.store.lock().unwrap();
            let approval = s
                .approvals
                .iter()
                .find(|a| a.id == approval_id && a.state == ApprovalState::Pending)
                .cloned()
                .ok_or("this approval is no longer pending")?;
            let run = s.run(&approval.run_id).cloned().ok_or("unknown run")?;
            let automation = s
                .automation(&approval.automation_id)
                .cloned()
                .ok_or("the automation was deleted")?;
            (automation, run, approval, s.halted)
        };
        let catalogue = Catalogue::bundled();
        let model = self.backends.model(&automation.model);
        let clock = self.clock.clone();
        let now = move || clock();
        let halted_now = || halted;
        let mut audit = |event: &str, data: Value| self.audit_event(event, data);
        let id_fn = new_id;
        let mut fs: Box<dyn FsTools> = if decision == Decision::Deny {
            Box::new(FailingFs("no tool is needed to deny".into()))
        } else {
            self.backends.fs(&automation.folder)?
        };
        let mut env = runner::Env {
            fs: fs.as_mut(),
            model: model.as_ref(),
            catalogue: &catalogue,
            grants: &[],
            halted: &halted_now,
            now: &now,
            audit: &mut audit,
            runs_today: 0,
            new_id: &id_fn,
        };
        let (run, grant) = runner::resume(&automation, run, &mut approval, decision, &mut env);
        let mut s = self.store.lock().unwrap();
        if let Some(a) = s.approvals.iter_mut().find(|a| a.id == approval.id) {
            *a = approval;
        }
        if let Some(g) = grant {
            s.grants.push(g);
        }
        let summary = RunSummary::from(&run);
        s.upsert_run(run);
        self.persist(&s);
        Ok(summary)
    }

    /// One scheduler tick: expires stale approvals and returns the automations due now (their slot is marked handled).
    pub fn due(&self) -> Vec<String> {
        let now = self.now();
        let mut s = self.store.lock().unwrap();
        let expired = s.expire_approvals(&stamp(now));
        for run in &expired {
            self.audit_event("approval.expired", json!({ "run": run }));
        }
        if s.halted {
            if !expired.is_empty() {
                self.persist(&s);
            }
            return vec![];
        }
        let mut due = vec![];
        for a in s.automations.iter_mut().filter(|a| a.enabled) {
            let created = schedule::parse_slot(&a.created_at).unwrap_or(now);
            let last = a.last_slot.as_deref().and_then(schedule::parse_slot);
            if let Some(slot) = schedule::due_slot(&a.schedule, created, last, now) {
                a.last_slot = Some(stamp(slot));
                due.push(a.id.clone());
            }
        }
        if !due.is_empty() || !expired.is_empty() {
            self.persist(&s);
        }
        due
    }
}

/// The tools of a run whose MCP server could not start: every call fails with the reason, and the run says it.
struct FailingFs(String);

impl FsTools for FailingFs {
    fn list_files(&mut self, _: &Path) -> Result<Vec<String>, String> {
        Err(self.0.clone())
    }
    fn read_bytes(&mut self, _: &Path) -> Result<Vec<u8>, String> {
        Err(self.0.clone())
    }
    fn write_text(&mut self, _: &Path, _: &str) -> Result<(), String> {
        Err(self.0.clone())
    }
}
