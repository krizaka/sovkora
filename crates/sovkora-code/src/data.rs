//! The data channel (ADR-093 §4.1): everything untrusted — the issue, its comments, file contents, search hits, check
//! output — travels as a fenced, labelled, size-capped, redacted part. Nothing untrusted is ever put in the system
//! prompt. The fence cannot be closed (or re-opened) from inside.

use super::visibility::redact;

/// One untrusted source, as the runner receives it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Part {
    pub source: String,
    pub attrs: Vec<(String, String)>,
    pub text: String,
}

// @fixed: size cap of one data part in the prompt, whatever the rule set says (the rule set can only lower it).
pub const PART_CAP: usize = 64 * 1024;

const TAG: &[u8] = b"untrusted";

/// Neutralises `<untrusted` and `</untrusted` (any case) so a text can neither close nor open a fence.
pub fn escape(text: &str) -> String {
    let bytes = text.as_bytes();
    let mut out = String::with_capacity(text.len());
    let mut i = 0;
    while i < text.len() {
        let mut skip = 0;
        if bytes[i] == b'<' {
            let rest = &bytes[i + 1..];
            let rest = rest.strip_prefix(b"/").unwrap_or(rest);
            if rest.len() >= TAG.len() && rest[..TAG.len()].eq_ignore_ascii_case(TAG) {
                skip = 1;
            }
        }
        if skip == 1 {
            out.push('‹');
            i += 1;
        } else {
            let c = text[i..].chars().next().unwrap_or(' ');
            out.push(c);
            i += c.len_utf8();
        }
    }
    out
}

fn attr_value(v: &str) -> String {
    v.chars()
        .filter(|c| !matches!(c, '"' | '<' | '>' | '\n' | '\r' | '\'' | '`'))
        .take(120)
        .collect()
}

/// `<untrusted source="…" k="v">` + the redacted, escaped, capped text + `</untrusted>`.
pub fn untrusted(source: &str, attrs: &[(&str, &str)], text: &str, cap: usize) -> String {
    let cap = cap.min(PART_CAP);
    let mut head = format!("<untrusted source=\"{}\"", attr_value(source));
    for (k, v) in attrs {
        let key: String = k
            .chars()
            .filter(|c| c.is_ascii_alphanumeric() || *c == '-' || *c == '_')
            .collect();
        if !key.is_empty() && key != "source" {
            head.push_str(&format!(" {key}=\"{}\"", attr_value(v)));
        }
    }
    head.push('>');
    let clean = redact(text);
    let (body, cut) = match clean.char_indices().nth(cap) {
        Some((at, _)) => (&clean[..at], true),
        None => (clean.as_str(), false),
    };
    let tail = if cut { "\n[truncated]" } else { "" };
    format!("{head}\n{}{tail}\n</untrusted>", escape(body))
}

pub fn fence_part(p: &Part, cap: usize) -> String {
    let attrs: Vec<(&str, &str)> = p
        .attrs
        .iter()
        .map(|(k, v)| (k.as_str(), v.as_str()))
        .collect();
    untrusted(&p.source, &attrs, &p.text, cap)
}
