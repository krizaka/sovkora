//! The agent loop (ADR-093 §3, §4, §6): the runner owns the tools, the approvals and the audit; the engine owns the model
//! turn. Each turn is a step of a Studio run, so the pack gate (entitlement, consent, credits) is read by the engine on
//! **every** turn: a refusal ends the run with the engine's code. The model gets a closed tool list and nothing else.

use super::checks::{self, Book, CheckError, CheckRun, Limits};
use super::data::{self, Part};
use super::diff::{self, FileProposal};
use super::scope::Repo;
use super::secret::Secret;
use super::tools;
use super::visibility::redact;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::time::Duration;

/// The routing feature key (ADR-029): the engine picks the model, the Mac never names a provider.
pub const FEATURE_KEY: &str = "agent.code.issue-to-pr";

/// The system prompt: a fixed template, no placeholder. Untrusted text never goes in it (a governance test reads it).
pub const SYSTEM_PROMPT: &str = include_str!("prompt.md");

// @fixed: ceilings on what the settings rule set may grant a run.
pub const MAX_TURNS: usize = 40;
pub const MAX_TOKENS: u64 = 1_000_000;
pub const MAX_FILES_READ: usize = 200;
pub const MAX_HUNKS: usize = 200;

/// What the model may call. `effect` is data (ADR-079 §3): `propose_diff` writes nothing, `run_check` is the one `exec`.
pub struct ToolDef {
    pub name: &'static str,
    pub effect: &'static str,
    pub description: &'static str,
    pub params: &'static [&'static str],
}

pub const TOOLS: &[ToolDef] = &[
    ToolDef { name: "read_file", effect: "read", description: "Read a text file of the repository.", params: &["path"] },
    ToolDef { name: "list_dir", effect: "read", description: "List a folder of the repository (empty path = the root).", params: &["path"] },
    ToolDef { name: "search_text", effect: "read", description: "Literal search over the visible files.", params: &["query"] },
    ToolDef { name: "outline", effect: "read", description: "The declarations of a file, by line.", params: &["path"] },
    ToolDef { name: "propose_diff", effect: "write", description: "Propose the full new text of a file. Nothing is written until a person approves each hunk.", params: &["path", "new_text"] },
    ToolDef { name: "run_check", effect: "exec", description: "Run a check the person confirmed, by its name.", params: &["name"] },
];

pub fn tool_defs() -> Vec<Value> {
    TOOLS
        .iter()
        .map(|t| {
            let props: serde_json::Map<String, Value> = t.params.iter().map(|p| (p.to_string(), json!({ "type": "string" }))).collect();
            json!({ "name": t.name, "effect": t.effect, "description": t.description,
                    "parameters": { "type": "object", "properties": props, "required": t.params, "additionalProperties": false } })
        })
        .collect()
}

// ---- the engine port ----------------------------------------------------------------------------------------------

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
pub struct Message {
    /// `user` | `assistant` | `tool`.
    pub role: &'static str,
    pub content: String,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub calls: Vec<ToolCall>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tool_call_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ToolCall {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub args: Value,
}

#[derive(Debug, Clone, Copy, Default, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Usage {
    #[serde(default)]
    pub prompt_tokens: u64,
    #[serde(default)]
    pub completion_tokens: u64,
    /// Credits settled for the turn, in millionths.
    #[serde(default)]
    pub credits_micro: u64,
}

#[derive(Debug, Clone, Deserialize, PartialEq, Eq)]
pub struct TurnReply {
    #[serde(default)]
    pub text: String,
    #[serde(default)]
    pub calls: Vec<ToolCall>,
    #[serde(default)]
    pub usage: Usage,
}

pub struct TurnRequest<'a> {
    pub run_id: &'a str,
    pub feature_key: &'a str,
    pub system: &'a str,
    pub messages: &'a [Message],
    pub tools: &'a [Value],
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum EngineError {
    /// `studio_not_entitled`: the workspace has no entitlement for the Studio.
    NotEntitled,
    /// `consent_required`: the consent statement is missing or has a newer version.
    ConsentRequired,
    InsufficientCredits,
    Unavailable(String),
}

impl EngineError {
    pub fn code(&self) -> String {
        match self {
            EngineError::NotEntitled => "studio_not_entitled".into(),
            EngineError::ConsentRequired => "consent_required".into(),
            EngineError::InsufficientCredits => "insufficient_credits".into(),
            EngineError::Unavailable(m) => format!("engine_unavailable: {m}"),
        }
    }
}

/// The engine's refusals as the wire says them (`RestErrorResolver`): a `status` field in the body.
pub fn engine_error_from(http: u16, body: &str) -> EngineError {
    let status = serde_json::from_str::<Value>(body)
        .ok()
        .and_then(|v| v["status"].as_str().map(String::from))
        .unwrap_or_default();
    match status.as_str() {
        "studio_not_entitled" => EngineError::NotEntitled,
        "consent_required" => EngineError::ConsentRequired,
        "insufficient_credits" => EngineError::InsufficientCredits,
        _ => EngineError::Unavailable(format!("HTTP {http}")),
    }
}

/// One model turn as a step of a Studio run. The gate lives behind it.
pub trait Engine {
    fn turn(&mut self, req: &TurnRequest) -> Result<TurnReply, EngineError>;
    /// The run the turns belong to, once the engine has one (the task's id for the audit, the proof and the billing).
    fn run_id(&self) -> Option<String> {
        None
    }
    /// The task is over: the engine closes its session so the server stops waiting for turns and settles the run. Best
    /// effort and idempotent; if it fails, the server's idle TTL closes the session anyway.
    fn finish(&mut self) {}
}

/// One session = one Studio run per task (ADR-086, ADR-093 §6: one run id for the audit, the proof and the billing), then
/// `POST /api/v1/studio-runs/{runId}/turns` for every model turn. The server re-checks the entitlement, the consent and
/// the credits on every turn, picks the routing key from the Studio (a key sent by the app is ignored) and passes the
/// engine's refusals on in its own words. A remote engine must be https (the excerpts leave the Mac).
pub struct SessionEngine {
    base_url: String,
    bearer: Secret,
    /// `code-issue-to-pr` or `code-read-write`.
    studio_key: String,
    /// The task's run, started by the first turn and kept for all the others.
    run_id: Option<String>,
    agent: ureq::Agent,
}

/// Why an engine address is refused.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum EngineUrlError {
    /// Not a URL.
    Invalid(String),
    /// Not `https://`, and not plain http to this machine (the excerpts and the bearer would travel in clear).
    NotHttps(String),
}

impl std::fmt::Display for EngineUrlError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            EngineUrlError::Invalid(u) => write!(f, "not an engine address: {u}"),
            EngineUrlError::NotHttps(u) => {
                write!(
                    f,
                    "an engine outside this machine must be reached over https: {u}"
                )
            }
        }
    }
}

impl std::error::Error for EngineUrlError {}

/// `POST {base}/api/v1/studios/{studio}/runs`: starts the task's run.
pub fn start_run_url(base: &str, studio_key: &str) -> String {
    format!(
        "{}/api/v1/studios/{studio_key}/runs",
        base.trim_end_matches('/')
    )
}

/// `POST {base}/api/v1/studio-runs/{runId}/finish`: the task is over, close the session.
pub fn finish_url(base: &str, run_id: &str) -> String {
    format!(
        "{}/api/v1/studio-runs/{run_id}/finish",
        base.trim_end_matches('/')
    )
}

/// `POST {base}/api/v1/studio-runs/{runId}/turns`: one model turn of that run.
pub fn turns_url(base: &str, run_id: &str) -> String {
    format!(
        "{}/api/v1/studio-runs/{run_id}/turns",
        base.trim_end_matches('/')
    )
}

/// The wire of a turn: the system prompt, the messages and the closed tool list. No feature key: it is the server's.
pub fn turn_body(req: &TurnRequest) -> Value {
    json!({ "system": req.system, "messages": req.messages, "tools": req.tools })
}

impl SessionEngine {
    /// Refuses an address that is not https (plain http only to this machine, for tests and a local engine).
    pub fn new(
        base_url: &str,
        bearer: Secret,
        studio_key: String,
        timeout: Duration,
    ) -> Result<SessionEngine, EngineUrlError> {
        validate_base_url(base_url)?;
        Ok(SessionEngine {
            base_url: base_url.to_string(),
            bearer,
            studio_key,
            run_id: None,
            // a redirect would carry the bearer to an address nobody checked
            agent: ureq::AgentBuilder::new()
                .timeout(timeout)
                .redirects(0)
                .build(),
        })
    }

    fn post(
        &self,
        url: &str,
        body: Value,
        idempotency_key: Option<&str>,
    ) -> Result<Value, EngineError> {
        let mut request = self
            .agent
            .post(url)
            .set("Authorization", &format!("Bearer {}", self.bearer.expose()));
        if let Some(key) = idempotency_key {
            request = request.set("Idempotency-Key", key);
        }
        match request.send_json(body) {
            Ok(r) => r
                .into_json::<Value>()
                .map_err(|e| EngineError::Unavailable(e.to_string())),
            Err(ureq::Error::Status(code, resp)) => Err(engine_error_from(
                code,
                &resp.into_string().unwrap_or_default(),
            )),
            Err(e) => Err(EngineError::Unavailable(e.to_string())),
        }
    }

    /// The task's run: started once (a refusal such as `studio_not_entitled` answers here), then reused.
    fn ensure_run(&mut self) -> Result<String, EngineError> {
        if let Some(id) = &self.run_id {
            return Ok(id.clone());
        }
        let accepted = self.post(
            &start_run_url(&self.base_url, &self.studio_key),
            json!({ "inputs": {} }),
            Some(&uuid::Uuid::new_v4().to_string()),
        )?;
        let id = accepted["id"]
            .as_str()
            .ok_or_else(|| EngineError::Unavailable("no run id".into()))?
            .to_string();
        self.run_id = Some(id.clone());
        Ok(id)
    }
}

/// A remote engine over https only; plain http only to this machine (a loopback address or `localhost`).
fn validate_base_url(base_url: &str) -> Result<(), EngineUrlError> {
    let url =
        url::Url::parse(base_url).map_err(|_| EngineUrlError::Invalid(base_url.to_string()))?;
    let loopback = match url.host() {
        Some(url::Host::Domain(name)) => name.eq_ignore_ascii_case("localhost"),
        Some(url::Host::Ipv4(ip)) => ip.is_loopback(),
        Some(url::Host::Ipv6(ip)) => ip.is_loopback(),
        None => false,
    };
    match url.scheme() {
        "https" => Ok(()),
        "http" if loopback => Ok(()),
        _ => Err(EngineUrlError::NotHttps(base_url.to_string())),
    }
}

impl Engine for SessionEngine {
    fn turn(&mut self, req: &TurnRequest) -> Result<TurnReply, EngineError> {
        let run_id = self.ensure_run()?;
        let reply = self.post(&turns_url(&self.base_url, &run_id), turn_body(req), None)?;
        serde_json::from_value::<TurnReply>(reply)
            .map_err(|e| EngineError::Unavailable(e.to_string()))
    }

    fn run_id(&self) -> Option<String> {
        self.run_id.clone()
    }

    fn finish(&mut self) {
        // A session that never started has nothing to close; a failure is left to the server's idle TTL.
        if let Some(id) = self.run_id.take() {
            let _ = self.post(&finish_url(&self.base_url, &id), json!({}), None);
        }
    }
}

// ---- budgets, input, report ---------------------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Budgets {
    pub turns: usize,
    pub tokens: u64,
    pub files_read: usize,
    pub hunks: usize,
}

impl Budgets {
    /// The settings rule set's values, clamped to what the code allows.
    pub fn bounded(turns: usize, tokens: u64, files_read: usize, hunks: usize) -> Budgets {
        Budgets {
            turns: turns.clamp(1, MAX_TURNS),
            tokens: tokens.clamp(1, MAX_TOKENS),
            files_read: files_read.clamp(1, MAX_FILES_READ),
            hunks: hunks.clamp(1, MAX_HUNKS),
        }
    }
}

impl Default for Budgets {
    fn default() -> Self {
        Budgets::bounded(12, 200_000, 60, 40)
    }
}

/// The person approves a check that follows a « changes what runs » hunk (the session is tainted).
pub trait Approver {
    fn approve_check(&mut self, name: &str) -> bool;
}

pub struct AgentInput {
    pub run_id: String,
    /// The person's own words, typed in the app (not data from a repository or an issue).
    pub goal: String,
    /// The untrusted parts: the issue, its comments.
    pub data: Vec<Part>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Stop {
    Done,
    /// The engine refused a turn; the code is the engine's (`studio_not_entitled`, `consent_required`…).
    Refused(String),
    /// `SKIPPED_BUDGET`: a budget ran out; the report holds what was done.
    SkippedBudget(String),
}

#[derive(Debug, Clone)]
pub struct RunReport {
    pub run_id: String,
    pub stop: Stop,
    pub turns: usize,
    pub usage: Usage,
    pub files_read: usize,
    pub proposals: Vec<FileProposal>,
    pub checks: Vec<CheckRun>,
    /// Names of tools the model asked for that are not on the list. Never executed.
    pub rejected: Vec<String>,
    pub final_text: String,
}

// ---- the loop -----------------------------------------------------------------------------------------------------

fn redacted(history: &[Message]) -> Vec<Message> {
    history
        .iter()
        .map(|m| Message {
            role: m.role,
            content: redact(&m.content),
            calls: m
                .calls
                .iter()
                .map(|c| ToolCall {
                    id: c.id.clone(),
                    name: c.name.clone(),
                    args: serde_json::from_str(&redact(&c.args.to_string())).unwrap_or(Value::Null),
                })
                .collect(),
            tool_call_id: m.tool_call_id.clone(),
        })
        .collect()
}

struct Ctx<'a> {
    repo: &'a Repo,
    limits: &'a Limits,
    budgets: &'a Budgets,
}

enum Outcome {
    Result(String),
    Budget(&'static str),
}

fn arg<'a>(args: &'a Value, key: &str) -> Result<&'a str, String> {
    args[key]
        .as_str()
        .ok_or_else(|| format!("missing argument: {key}"))
}

fn refusal(e: impl std::fmt::Display) -> String {
    let mut s = e.to_string();
    s.truncate(300);
    format!("refused: {s}")
}

fn execute(
    cx: &Ctx,
    book: &mut Book,
    approver: &mut dyn Approver,
    report: &mut RunReport,
    call: &ToolCall,
) -> Outcome {
    let a = &call.args;
    match call.name.as_str() {
        "read_file" | "outline" | "list_dir" | "search_text" => run_read_tool(cx, report, call),
        "propose_diff" => run_propose_diff(cx, report, a),
        "run_check" => run_check_tool(cx, book, approver, report, a),
        _ => unreachable!("only listed tools reach execute"),
    }
}

fn run_read_tool(cx: &Ctx, report: &mut RunReport, call: &ToolCall) -> Outcome {
    if call.name == "read_file" {
        if report.files_read >= cx.budgets.files_read {
            return Outcome::Budget("files read");
        }
        report.files_read += 1;
    }
    let text = read_tool(cx, &call.name, &call.args).unwrap_or_else(|e| e);
    Outcome::Result(text)
}

fn read_tool(cx: &Ctx, name: &str, a: &Value) -> Result<String, String> {
    match name {
        "read_file" => {
            let p = arg(a, "path")?;
            let f = tools::read_file(cx.repo, p).map_err(refusal)?;
            Ok(data::untrusted(
                "file",
                &[("path", p)],
                &f.text,
                data::PART_CAP,
            ))
        }
        "outline" => {
            let p = arg(a, "path")?;
            let s = tools::outline(cx.repo, p).map_err(refusal)?;
            let lines: Vec<String> = s
                .iter()
                .map(|x| format!("{}: {}", x.line, x.text))
                .collect();
            Ok(data::untrusted(
                "outline",
                &[("path", p)],
                &lines.join("\n"),
                data::PART_CAP,
            ))
        }
        "list_dir" => {
            let p = a["path"].as_str().unwrap_or("");
            let e = tools::list_dir(cx.repo, p).map_err(refusal)?;
            let lines: Vec<String> = e.iter().map(list_entry).collect();
            Ok(data::untrusted(
                "listing",
                &[("path", p)],
                &lines.join("\n"),
                data::PART_CAP,
            ))
        }
        _ => {
            let m = tools::search_text(cx.repo, arg(a, "query")?).map_err(refusal)?;
            let lines: Vec<String> = m
                .iter()
                .map(|x| format!("{}:{}: {}", x.path, x.line, x.text))
                .collect();
            Ok(data::untrusted(
                "search",
                &[],
                &lines.join("\n"),
                data::PART_CAP,
            ))
        }
    }
}

fn list_entry(x: &tools::Entry) -> String {
    if x.dir {
        format!("{}/", x.name)
    } else {
        x.name.clone()
    }
}

fn run_propose_diff(cx: &Ctx, report: &mut RunReport, a: &Value) -> Outcome {
    let (path, new_text) = match (arg(a, "path"), arg(a, "new_text")) {
        (Ok(p), Ok(t)) => (p, t),
        (Err(e), _) | (_, Err(e)) => return Outcome::Result(e),
    };
    let p = match diff::propose(cx.repo, path, new_text) {
        Err(e) => return Outcome::Result(refusal(e)),
        Ok(p) => p,
    };
    let others: usize = report
        .proposals
        .iter()
        .filter(|q| q.path != p.path)
        .map(|q| q.hunks.len())
        .sum();
    if others + p.hunks.len() > cx.budgets.hunks {
        return Outcome::Budget("hunks");
    }
    let flagged = if p.hunks.iter().any(|h| h.adds_secret) {
        "; a hunk holds a token-shaped string and is flagged"
    } else {
        ""
    };
    let msg = format!(
        "proposed {} hunk(s) for {} — pending: a person approves each hunk before anything is written{flagged}",
        p.hunks.len(),
        p.path
    );
    report.proposals.retain(|q| q.path != p.path);
    report.proposals.push(p);
    Outcome::Result(msg)
}

fn run_check_tool(
    cx: &Ctx,
    book: &mut Book,
    approver: &mut dyn Approver,
    report: &mut RunReport,
    a: &Value,
) -> Outcome {
    let name = match arg(a, "name") {
        Ok(n) => n,
        Err(e) => return Outcome::Result(e),
    };
    let mut result = checks::run(cx.repo, book, name, false, cx.limits);
    if matches!(result, Err(CheckError::ApprovalRequired)) {
        result = if approver.approve_check(name) {
            checks::run(cx.repo, book, name, true, cx.limits)
        } else {
            Err(CheckError::LimitReached(
                "the person did not approve this run".into(),
            ))
        };
    }
    match result {
        Ok(run) => {
            let head = format!(
                "check {} finished: exit {:?}, {} ms, timed out: {}",
                run.name, run.exit_code, run.duration_ms, run.timed_out
            );
            let body = data::untrusted(
                "check-output",
                &[("name", &run.name)],
                &run.output,
                data::PART_CAP,
            );
            report.checks.push(run);
            Outcome::Result(format!("{head}\n{body}"))
        }
        Err(CheckError::LimitReached(m)) if m.contains("runs in this session") => {
            Outcome::Budget("check runs")
        }
        Err(e) => Outcome::Result(refusal(e)),
    }
}

/// Runs the loop, then says « finish » to the engine: the end of a task closes the server's session (whatever the stop).
/// The caller starts a run with `Book::begin_run` (a new session, tainted from the first untrusted byte);
/// a loop resumed after the person applied hunks keeps its session, so a « changes what runs » hunk still asks.
pub fn run_agent(
    repo: &Repo,
    book: &mut Book,
    engine: &mut dyn Engine,
    approver: &mut dyn Approver,
    input: &AgentInput,
    budgets: &Budgets,
    limits: &Limits,
) -> RunReport {
    let report = run_loop(repo, book, engine, approver, input, budgets, limits);
    engine.finish();
    report
}

fn run_loop(
    repo: &Repo,
    book: &mut Book,
    engine: &mut dyn Engine,
    approver: &mut dyn Approver,
    input: &AgentInput,
    budgets: &Budgets,
    limits: &Limits,
) -> RunReport {
    let tools_json = tool_defs();
    let cx = Ctx {
        repo,
        limits,
        budgets,
    };
    let mut report = RunReport {
        run_id: input.run_id.clone(),
        stop: Stop::Done,
        turns: 0,
        usage: Usage::default(),
        files_read: 0,
        proposals: vec![],
        checks: vec![],
        rejected: vec![],
        final_text: String::new(),
    };

    let mut history = vec![Message {
        role: "user",
        content: input.goal.clone(),
        calls: vec![],
        tool_call_id: None,
    }];
    for part in &input.data {
        history.push(Message {
            role: "user",
            content: data::fence_part(part, data::PART_CAP),
            calls: vec![],
            tool_call_id: None,
        });
    }

    loop {
        if report.turns >= budgets.turns {
            report.stop = Stop::SkippedBudget("turns".into());
            break;
        }
        if report.usage.prompt_tokens + report.usage.completion_tokens >= budgets.tokens {
            report.stop = Stop::SkippedBudget("tokens".into());
            break;
        }
        let messages = redacted(&history);
        let req = TurnRequest {
            run_id: &input.run_id,
            feature_key: FEATURE_KEY,
            system: SYSTEM_PROMPT,
            messages: &messages,
            tools: &tools_json,
        };
        let answer = engine.turn(&req);
        if let Some(id) = engine.run_id() {
            report.run_id = id;
        }
        let reply = match answer {
            Ok(r) => r,
            Err(e) => {
                report.stop = Stop::Refused(e.code());
                break;
            }
        };
        report.turns += 1;
        report.usage.prompt_tokens += reply.usage.prompt_tokens;
        report.usage.completion_tokens += reply.usage.completion_tokens;
        report.usage.credits_micro += reply.usage.credits_micro;
        history.push(Message {
            role: "assistant",
            content: reply.text.clone(),
            calls: reply.calls.clone(),
            tool_call_id: None,
        });
        if reply.calls.is_empty() {
            report.final_text = redact(&reply.text);
            break;
        }
        for call in &reply.calls {
            let outcome = if TOOLS.iter().any(|t| t.name == call.name) {
                execute(&cx, book, approver, &mut report, call)
            } else {
                report.rejected.push(call.name.chars().take(64).collect());
                Outcome::Result("refused: that tool is not available".into())
            };
            match outcome {
                Outcome::Result(content) => history.push(Message {
                    role: "tool",
                    content,
                    calls: vec![],
                    tool_call_id: Some(call.id.clone()),
                }),
                Outcome::Budget(what) => {
                    report.stop = Stop::SkippedBudget(what.into());
                    return report;
                }
            }
        }
    }
    report
}
