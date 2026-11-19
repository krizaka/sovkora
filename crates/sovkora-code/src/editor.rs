//! « Open in VS Code / Xcode » at a line. The path goes through the read scope first.

use super::scope::{CodeError, Repo};
use serde::Deserialize;
use std::process::Command;

#[derive(Debug, Clone, Copy, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "kebab-case")]
pub enum Editor {
    VsCode,
    Xcode,
}

/// The program and arguments that open `abs` at `line` (1-based). Argv, no shell.
pub fn command(editor: Editor, abs: &str, line: usize) -> (String, Vec<String>) {
    let line = line.max(1);
    match editor {
        Editor::VsCode => (
            "open".into(),
            vec![
                "-a".into(),
                "Visual Studio Code".into(),
                "--args".into(),
                "-g".into(),
                format!("{abs}:{line}"),
            ],
        ),
        Editor::Xcode => (
            "xed".into(),
            vec!["--line".into(), line.to_string(), abs.to_string()],
        ),
    }
}

pub fn open(repo: &Repo, editor: Editor, rel: &str, line: usize) -> Result<(), CodeError> {
    let abs = repo.resolve_read(rel)?;
    let (program, args) = command(editor, &abs.to_string_lossy(), line);
    Command::new(program)
        .args(args)
        .spawn()
        .map_err(|e| CodeError::Io(e.to_string()))?;
    Ok(())
}
