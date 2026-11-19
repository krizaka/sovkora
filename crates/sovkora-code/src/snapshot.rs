//! Undo (ADR-093 §5.4): the pre-change state of the working tree is a commit under
//! `refs/orazaka/snapshots/<run-id>`, built with a private index so the user's index, branch and stash are untouched.
//! Restoring puts back, byte for byte, the files a run touched, and removes those it created.

use super::scope::{CodeError, Repo};
use std::fs;
use std::process::Command;

fn valid_run_id(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 64
        && id
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
}

fn ref_name(run_id: &str) -> Result<String, CodeError> {
    if valid_run_id(run_id) {
        Ok(format!("refs/orazaka/snapshots/{run_id}"))
    } else {
        Err(CodeError::Refused(format!("run id {run_id}")))
    }
}

fn git_private_index(
    repo: &Repo,
    index: &std::path::Path,
    args: &[&str],
) -> Result<String, CodeError> {
    let out = Command::new("git")
        .arg("-C")
        .arg(repo.root())
        .args(args)
        .env("GIT_INDEX_FILE", index)
        .env("GIT_AUTHOR_NAME", "Sovkora")
        .env("GIT_AUTHOR_EMAIL", "orazaka@localhost")
        .env("GIT_COMMITTER_NAME", "Sovkora")
        .env("GIT_COMMITTER_EMAIL", "orazaka@localhost")
        .output()
        .map_err(|e| CodeError::Io(e.to_string()))?;
    if out.status.success() {
        Ok(String::from_utf8_lossy(&out.stdout).trim().to_string())
    } else {
        Err(CodeError::Io(
            String::from_utf8_lossy(&out.stderr).trim().to_string(),
        ))
    }
}

/// Records the working tree as it is now (tracked and untracked, gitignored files left out). Returns the commit sha.
pub fn create(repo: &Repo, run_id: &str) -> Result<String, CodeError> {
    let name = ref_name(run_id)?;
    let dir =
        std::env::temp_dir().join(format!("orazaka-snapshot-{}-{run_id}", std::process::id()));
    fs::create_dir_all(&dir).map_err(|e| CodeError::Io(e.to_string()))?;
    let index = dir.join("index");
    let result = (|| {
        git_private_index(repo, &index, &["add", "-A", "--", "."])?;
        let tree = git_private_index(repo, &index, &["write-tree"])?;
        let sha = git_private_index(
            repo,
            &index,
            &[
                "commit-tree",
                &tree,
                "-m",
                &format!("orazaka snapshot {run_id}"),
            ],
        )?;
        repo.git(&["update-ref", &name, &sha])?;
        Ok(sha)
    })();
    let _ = fs::remove_dir_all(&dir);
    result
}

/// Puts back the `touched` files as the snapshot had them; a file the snapshot did not have is removed.
/// Every path goes through the write scope. Returns the restored paths.
pub fn restore(repo: &Repo, run_id: &str, touched: &[String]) -> Result<Vec<String>, CodeError> {
    let name = ref_name(run_id)?;
    repo.git(&["rev-parse", "--verify", &name])
        .map_err(|_| CodeError::NotFound(name.clone()))?;
    let mut done = Vec::new();
    for rel in touched {
        let target = repo.resolve_write(rel)?;
        let spec = format!("{name}:{rel}");
        match repo.git(&["cat-file", "blob", &spec]) {
            Ok(bytes) => {
                if let Some(parent) = target.parent() {
                    fs::create_dir_all(parent).map_err(|e| CodeError::Io(e.to_string()))?;
                }
                repo.resolve_write(rel)?;
                fs::write(&target, bytes).map_err(|e| CodeError::Io(e.to_string()))?;
            }
            Err(_) => match fs::remove_file(&target) {
                Ok(()) => {}
                Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
                Err(e) => return Err(CodeError::Io(e.to_string())),
            },
        }
        done.push(rel.clone());
    }
    Ok(done)
}
