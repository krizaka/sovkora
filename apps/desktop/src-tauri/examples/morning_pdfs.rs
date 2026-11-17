//! The journey of ADR-079 slice 1, run for real from the command line — the same `Service` the app drives:
//! the filesystem MCP server limited to the folder, a model served on this Mac (Ollama by default), real PDFs.
//!
//!   cargo run --example morning_pdfs -- <folder> <data-dir> [model] [base-url]
//!
//! It creates "Morning PDF summaries" due now, runs it (it stops at the approval), approves always for the folder,
//! and prints the run, its trace, the usage and whether the audit chain verifies. Each state is also written as JSON
//! in `<data-dir>/journey/` (the evidence a PR or a recording refers to).

use orazaka_desktop_lib::automation::{
    now_local, schedule::format_time_of_day, types::*, NewAutomation, RealBackends, Service,
};
use std::path::Path;
use std::sync::Arc;

fn dump(dir: &Path, name: &str, value: &impl serde::Serialize) {
    std::fs::create_dir_all(dir).unwrap();
    std::fs::write(dir.join(name), serde_json::to_string_pretty(value).unwrap()).unwrap();
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let folder = Path::new(args.get(1).expect("folder"))
        .canonicalize()
        .expect("the folder exists");
    let data = Path::new(args.get(2).expect("data dir")).to_path_buf();
    let model = args.get(3).cloned().unwrap_or_else(|| "qwen2.5:7b".into());
    let base_url = args
        .get(4)
        .cloned()
        .unwrap_or_else(|| "http://localhost:11434/v1".into());
    let journey = data.join("journey");

    let svc = Service::open(
        &data,
        Arc::new(RealBackends),
        Arc::new(now_local),
        Arc::new(|| {}),
    );
    // Scheduled for the next minute; the loop below is the scheduler tick of the app.
    let time = format_time_of_day(now_local() + time::Duration::minutes(1));
    let a = svc
        .create(
            NewAutomation {
                name: "Morning PDF summaries".into(),
                folder: folder.display().to_string(),
                time,
                days: vec![],
                model,
                base_url,
                dry_run: false,
            },
            |p: &Path| p.canonicalize().map_err(|e| e.to_string()),
        )
        .expect("created");
    dump(&journey, "1-created.json", &svc.overview());

    let mut due = svc.due();
    for _ in 0..30 {
        if !due.is_empty() {
            break;
        }
        std::thread::sleep(std::time::Duration::from_secs(5));
        due = svc.due();
    }
    assert_eq!(due, vec![a.id.clone()], "the automation is due");
    let run_id = svc.run_now(&a.id, RunTrigger::Schedule).expect("ran");
    let run = svc.run(&run_id).unwrap();
    println!("run {} · job {} · {:?}", run.id, run.job_id, run.status);
    dump(&journey, "2-awaiting-approval.json", &svc.overview());
    dump(&journey, "2-run.json", &run);

    let approval = svc
        .overview()
        .approvals
        .first()
        .cloned()
        .expect("an approval is asked before the first write");
    let done = svc.decide(&approval.id, Decision::Always).expect("decided");
    let run = svc.run(&run_id).unwrap();
    dump(&journey, "3-approved.json", &svc.overview());
    dump(&journey, "3-run.json", &run);
    let audit = svc.audit_view(500);
    dump(&journey, "4-audit.json", &audit);

    for s in &run.steps {
        println!("  {:>2} {:<28} {:?} {}", s.seq, s.name, s.status, s.detail);
    }
    println!(
        "{:?} · {} note(s) · {} → {} tokens · {} ms · audit chain ok: {} ({} entries)",
        done.status,
        done.written,
        run.usage.prompt_tokens,
        run.usage.completion_tokens,
        run.usage.duration_ms,
        audit.verification.ok,
        audit.verification.entries
    );
}
