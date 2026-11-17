//! End to end, without mocks of our own code: the real `Service`, the real filesystem MCP server
//! (`@modelcontextprotocol/server-filesystem`, pinned in mcp-catalogue.json, started through npx with the folder as its
//! only allowed directory), real PDFs, and an OpenAI-compatible model served on 127.0.0.1 by this test (the same API
//! Ollama serves) — so CI needs neither a GPU nor a model download.
//!
//! The journey of slice 1: "every morning at 8:00, summarise the new PDFs in Inbox and save a note next to them".

use orazaka_desktop_lib::automation::schedule::{parse_slot, LocalDateTime};
use orazaka_desktop_lib::automation::{
    self, testpdf::minimal_pdf, types::*, NewAutomation, RealBackends, Service,
};
use std::io::{BufRead, BufReader, Read, Write};
use std::net::TcpListener;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};

/// Both journeys start the filesystem server through `npx`. Started together on a cold npm cache (CI), the two
/// installs race on the same cache folder and one server exits at once; so the journeys take turns.
static ONE_NPX_AT_A_TIME: Mutex<()> = Mutex::new(());

/// An OpenAI-compatible `chat/completions` endpoint that answers a fixed summary and counts the calls.
fn model_server() -> (String, Arc<AtomicUsize>) {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let url = format!(
        "http://127.0.0.1:{}/v1",
        listener.local_addr().unwrap().port()
    );
    let calls = Arc::new(AtomicUsize::new(0));
    let counter = calls.clone();
    std::thread::spawn(move || {
        for stream in listener.incoming().flatten() {
            let mut reader = BufReader::new(stream.try_clone().unwrap());
            let mut len = 0usize;
            let mut line = String::new();
            loop {
                line.clear();
                if reader.read_line(&mut line).unwrap_or(0) == 0 || line == "\r\n" {
                    break;
                }
                if let Some(v) = line.to_ascii_lowercase().strip_prefix("content-length:") {
                    len = v.trim().parse().unwrap_or(0);
                }
            }
            let mut body = vec![0; len];
            let _ = reader.read_exact(&mut body);
            let request: serde_json::Value = serde_json::from_slice(&body).unwrap_or_default();
            let user = request["messages"][1]["content"]
                .as_str()
                .unwrap_or_default()
                .to_string();
            counter.fetch_add(1, Ordering::SeqCst);
            let file = user
                .lines()
                .next()
                .unwrap_or_default()
                .trim_start_matches("File: ")
                .to_string();
            let answer = serde_json::json!({
                "choices": [{ "message": { "role": "assistant", "content": format!("Summary of {file}.\n- Key fact\nAction: none") } }],
                "usage": { "prompt_tokens": 120, "completion_tokens": 30 }
            })
            .to_string();
            let mut out = stream;
            let _ = write!(out, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{answer}", answer.len());
        }
    });
    (url, calls)
}

fn at(s: &str) -> LocalDateTime {
    parse_slot(&s.replace(' ', "T")).unwrap()
}

#[test]
fn every_morning_the_new_pdfs_of_inbox_get_a_summary_note_after_one_approval() {
    let _turn = ONE_NPX_AT_A_TIME.lock().unwrap_or_else(|e| e.into_inner());
    let home = tempfile::tempdir().unwrap();
    let inbox = home.path().join("Inbox");
    std::fs::create_dir_all(&inbox).unwrap();
    std::fs::write(
        inbox.join("lease-2026.pdf"),
        minimal_pdf("Residential lease\nTenant: Eric Tremblay\nRent: 1,250 $ per month"),
    )
    .unwrap();
    std::fs::write(
        inbox.join("invoice-0042.pdf"),
        minimal_pdf("Invoice 0042\nAmount due: 480 $\nDue: 2026-11-01"),
    )
    .unwrap();
    std::fs::write(inbox.join("notes.txt"), "not a pdf").unwrap();
    let inbox = inbox.canonicalize().unwrap();
    let data = tempfile::tempdir().unwrap();
    let (model_url, calls) = model_server();

    let clock = Arc::new(Mutex::new(at("2026-10-16 07:00:00")));
    let c = clock.clone();
    let svc = Service::open(
        data.path(),
        Arc::new(RealBackends),
        Arc::new(move || *c.lock().unwrap()),
        Arc::new(|| {}),
    );

    let a = svc
        .create(
            NewAutomation {
                name: "Morning PDFs".into(),
                folder: inbox.display().to_string(),
                time: "08:00".into(),
                days: vec![],
                model: "qwen2.5:7b".into(),
                base_url: model_url,
                dry_run: false,
            },
            |p: &Path| p.canonicalize().map_err(|e| e.to_string()),
        )
        .unwrap();

    // 07:59 nothing is due; 08:00:20 the scheduler starts it.
    *clock.lock().unwrap() = at("2026-10-16 07:59:40");
    assert!(svc.due().is_empty());
    *clock.lock().unwrap() = at("2026-10-16 08:00:20");
    assert_eq!(svc.due(), vec![a.id.clone()]);
    let run_id = svc.run_now(&a.id, RunTrigger::Schedule).unwrap();

    // First time: it asks before writing, and nothing is written.
    let run = svc.run(&run_id).unwrap();
    assert_eq!(run.status, RunStatus::AwaitingApproval, "{:#?}", run.steps);
    assert_eq!(calls.load(Ordering::SeqCst), 2);
    assert!(!inbox.join("lease-2026.summary.md").exists());
    let overview = svc.overview();
    assert_eq!(overview.approvals.len(), 1);
    let approval = overview.approvals[0].clone();
    assert_eq!(approval.writes.len(), 2);

    // "Approve always for this folder": the notes are written next to the PDFs, through MCP.
    let done = svc.decide(&approval.id, Decision::Always).unwrap();
    assert_eq!(done.status, RunStatus::Succeeded);
    assert_eq!(done.written, 2);
    let note = std::fs::read_to_string(inbox.join("lease-2026.summary.md")).unwrap();
    assert!(note.starts_with("# lease-2026 — summary"), "{note}");
    assert!(note.contains("Summary of lease-2026.pdf."), "{note}");
    assert!(inbox.join("invoice-0042.summary.md").exists());
    assert!(!inbox.join("notes.summary.md").exists());
    assert_eq!(svc.overview().grants.len(), 1);

    // The trace says what happened, step by step.
    let trace: Vec<(StepKind, String)> = svc
        .run(&run_id)
        .unwrap()
        .steps
        .iter()
        .map(|s| (s.kind, s.name.clone()))
        .collect();
    assert_eq!(trace.first().unwrap().1, "filesystem.list_directory");
    assert!(trace
        .iter()
        .any(|(k, n)| *k == StepKind::Approval && n == "approval"));
    assert_eq!(
        trace
            .iter()
            .filter(|(_, n)| n == "filesystem.write_file")
            .count(),
        2
    );

    // Next morning, a new PDF: written without asking (the grant), the old ones are not summarised again.
    std::fs::write(
        inbox.join("contract.pdf"),
        minimal_pdf("Service contract\nTerm: 12 months"),
    )
    .unwrap();
    *clock.lock().unwrap() = at("2026-10-17 08:00:10");
    assert_eq!(svc.due(), vec![a.id.clone()]);
    let run2 = svc
        .run(&svc.run_now(&a.id, RunTrigger::Schedule).unwrap())
        .unwrap();
    assert_eq!(run2.status, RunStatus::Succeeded, "{:#?}", run2.steps);
    assert_eq!(run2.written, vec![inbox.join("contract.summary.md")]);
    assert_eq!(calls.load(Ordering::SeqCst), 3);
    assert!(run2.usage.prompt_tokens > 0 && !run2.job_id.is_empty());

    // The kill switch stops the next run before any tool or model call.
    svc.set_halted(true);
    std::fs::write(inbox.join("late.pdf"), minimal_pdf("Late")).unwrap();
    let halted = svc
        .run(&svc.run_now(&a.id, RunTrigger::Manual).unwrap())
        .unwrap();
    assert_eq!(halted.status, RunStatus::Halted);
    assert_eq!(calls.load(Ordering::SeqCst), 3);
    assert!(!inbox.join("late.summary.md").exists());

    // Everything is in the audit log, and the chain verifies.
    let audit = svc.audit_view(500);
    assert!(audit.verification.ok);
    let events: Vec<String> = audit.entries.iter().map(|e| e.event.clone()).collect();
    for e in [
        "automation.created",
        "run.started",
        "approval.requested",
        "approval.decided",
        "grant.created",
        "tool.called",
        "kill-switch.on",
        "run.finished",
    ] {
        assert!(events.iter().any(|x| x == e), "missing {e} in {events:?}");
    }
    // The audit never holds the content of a document.
    let raw = std::fs::read_to_string(data.path().join("audit.jsonl")).unwrap();
    assert!(!raw.contains("Eric Tremblay") && !raw.contains("Summary of"));
}

#[test]
fn the_filesystem_server_cannot_be_used_outside_the_folder() {
    let _turn = ONE_NPX_AT_A_TIME.lock().unwrap_or_else(|e| e.into_inner());
    use automation::mcp::{Catalogue, FsTools, McpFilesystem};
    let home = tempfile::tempdir().unwrap();
    let inbox = home.path().join("Inbox");
    std::fs::create_dir_all(&inbox).unwrap();
    std::fs::write(home.path().join("secret.txt"), "secret").unwrap();
    let inbox = inbox.canonicalize().unwrap();
    let mut fs = McpFilesystem::start(
        &Catalogue::bundled(),
        &inbox,
        std::time::Duration::from_secs(120),
    )
    .unwrap();
    let outside: PathBuf = inbox.parent().unwrap().join("secret.txt");
    assert!(fs.read_bytes(&outside).is_err());
    assert!(fs.write_text(&outside, "x").is_err());
    assert!(fs.write_text(&inbox.join("../escape.txt"), "x").is_err());
    assert!(!home.path().join("escape.txt").exists());
    assert_eq!(
        std::fs::read_to_string(home.path().join("secret.txt")).unwrap(),
        "secret"
    );
}
