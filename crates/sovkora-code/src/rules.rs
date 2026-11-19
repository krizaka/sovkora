//! The settings rule set for code runs (ADR-093 §8): budgets and check limits are data in `code-rules.json`, bounded in
//! code. A missing or broken file means the defaults; a value can never go above the code's ceiling.

use super::agent::Budgets;
use super::checks::Limits;
use serde::Deserialize;
use std::fs;
use std::path::Path;

#[derive(Debug, Clone, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", default)]
pub struct CodeRules {
    pub turns: usize,
    pub tokens: u64,
    pub files_read: usize,
    pub hunks: usize,
    pub check_runs: usize,
    pub check_wall_secs: u64,
    pub check_output_cap: usize,
}

impl Default for CodeRules {
    fn default() -> Self {
        let (b, l) = (Budgets::default(), Limits::default());
        CodeRules {
            turns: b.turns,
            tokens: b.tokens,
            files_read: b.files_read,
            hunks: b.hunks,
            check_runs: l.max_runs,
            check_wall_secs: l.max_wall_secs,
            check_output_cap: l.output_cap,
        }
    }
}

impl CodeRules {
    pub fn load(file: &Path) -> CodeRules {
        fs::read_to_string(file)
            .ok()
            .and_then(|s| serde_json::from_str(&s).ok())
            .unwrap_or_default()
    }

    pub fn budgets(&self) -> Budgets {
        Budgets::bounded(self.turns, self.tokens, self.files_read, self.hunks)
    }

    pub fn limits(&self) -> Limits {
        Limits::bounded(self.check_runs, self.check_wall_secs, self.check_output_cap)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_missing_or_broken_file_is_the_defaults() {
        let tmp = tempfile::tempdir().unwrap();
        assert_eq!(
            CodeRules::load(&tmp.path().join("none.json")),
            CodeRules::default()
        );
        let f = tmp.path().join("r.json");
        fs::write(&f, "not json").unwrap();
        assert_eq!(CodeRules::load(&f), CodeRules::default());
    }

    #[test]
    fn the_file_sets_values_and_the_code_bounds_them() {
        let tmp = tempfile::tempdir().unwrap();
        let f = tmp.path().join("r.json");
        fs::write(&f, r#"{"turns":5,"tokens":99999999999,"checkRuns":3}"#).unwrap();
        let r = CodeRules::load(&f);
        assert_eq!(r.budgets().turns, 5);
        assert_eq!(r.budgets().tokens, super::super::agent::MAX_TOKENS);
        assert_eq!(r.limits().max_runs, 3);
        assert_eq!(r.budgets().hunks, Budgets::default().hunks);
    }
}
