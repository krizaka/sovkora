//! Slice 4 of ADR-093: the connection to GitHub, by the OAuth device flow of the GitHub App « Orazaka for Mac ».
//!
//! The flow, the refresh and the Keychain vault are the kit's (`krizaka_rust::device_flow`); this module holds what
//! is Orazaka's: the App's public client id (a settings row, so a firm on GitHub Enterprise registers its own), the HTTP
//! transport, and the state the page polls. The tokens stay on the Rust side: the page sees `connected` and, while a
//! sign-in waits, the code to type and where. No client secret exists on the Mac.

use krizaka_rust::device_flow::{
    self, DeviceCode, Failure, Provider, Session, TokenVault, Tokens, Transport, TransportError,
};
use krizaka_rust::secrets::SecretStore;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::fs;
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

/// The public client id of the GitHub App « Orazaka for Mac » (App ID 5549153). Public by design (RFC 8628: no secret).
// @fixed: the registered identity of the App, overridable in `github.json`
pub const DEFAULT_CLIENT_ID: &str = "Iv23ctICTKzOpI513c8K";
/// The Keychain keys are `github.access`, `github.refresh`, `github.expires`.
// @fixed: the prefix of the Keychain keys
const VAULT_PREFIX: &str = "github";
const HTTP_TIMEOUT: Duration = Duration::from_secs(20);

/// `github.json` in the data directory: for GitHub Enterprise, or another registered App.
#[derive(Debug, Clone, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", default)]
pub struct GithubConfig {
    pub client_id: String,
    pub device_code_url: String,
    pub token_url: String,
}

impl Default for GithubConfig {
    fn default() -> Self {
        let p = Provider::github(DEFAULT_CLIENT_ID);
        GithubConfig {
            client_id: p.client_id,
            device_code_url: p.device_code_url,
            token_url: p.token_url,
        }
    }
}

impl GithubConfig {
    /// A missing or broken file means the defaults; an address that is not https is ignored (a token never goes in clear).
    pub fn load(file: &Path) -> Self {
        let defaults = GithubConfig::default();
        let Some(mut c) = fs::read_to_string(file)
            .ok()
            .and_then(|t| serde_json::from_str::<GithubConfig>(&t).ok())
        else {
            return defaults;
        };
        if c.client_id.trim().is_empty() {
            c.client_id = defaults.client_id.clone();
        }
        if !c.device_code_url.starts_with("https://") {
            c.device_code_url = defaults.device_code_url.clone();
        }
        if !c.token_url.starts_with("https://") {
            c.token_url = defaults.token_url.clone();
        }
        c
    }

    pub fn provider(&self) -> Provider {
        Provider {
            device_code_url: self.device_code_url.clone(),
            token_url: self.token_url.clone(),
            client_id: self.client_id.clone(),
        }
    }
}

/// One form POST to GitHub, answered as JSON (GitHub answers 200 with an `error` field for the pending states).
pub struct HttpTransport;

impl Transport for HttpTransport {
    fn post_form(&self, url: &str, fields: &[(&str, &str)]) -> Result<Value, TransportError> {
        let result = ureq::post(url)
            .timeout(HTTP_TIMEOUT)
            .set("Accept", "application/json")
            .send_form(fields);
        match result {
            Ok(resp) => resp
                .into_json::<Value>()
                .map_err(|e| TransportError(e.to_string())),
            // an HTTP error still carries GitHub's `error` code
            Err(ureq::Error::Status(_, resp)) => resp
                .into_json::<Value>()
                .map_err(|e| TransportError(e.to_string())),
            Err(e) => Err(TransportError(e.to_string())),
        }
    }
}

/// What the page polls: connected, waiting for the person (code to type), or a failure to show.
#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct GithubStatus {
    pub connected: bool,
    pub pending: Option<DeviceCode>,
    pub failure: Option<String>,
}

#[derive(Default)]
struct Flow {
    pending: Option<DeviceCode>,
    failure: Option<String>,
    cancelled: Arc<AtomicBool>,
}

/// The sign-in in progress, shared between the commands and the waiting thread.
#[derive(Clone, Default)]
pub struct Connection(Arc<Mutex<Flow>>);

fn unix_now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

impl Connection {
    pub fn status(&self, store: &dyn SecretStore) -> Result<GithubStatus, String> {
        let flow = self.0.lock().map_err(|e| e.to_string())?;
        let Session { connected } = TokenVault::new(store, VAULT_PREFIX)
            .session()
            .map_err(|e| e.to_string())?;
        Ok(GithubStatus {
            connected,
            pending: flow.pending.clone(),
            failure: flow.failure.clone(),
        })
    }

    /// Asks GitHub for a code and returns it at once; `wait` then runs on its own thread until the person approves.
    pub fn begin(
        &self,
        transport: Arc<dyn Transport + Send + Sync>,
        store: Arc<dyn SecretStore>,
        config: &GithubConfig,
        spawn: impl FnOnce(Box<dyn FnOnce() + Send>),
    ) -> Result<DeviceCode, String> {
        let provider = config.provider();
        // No `scope`: a GitHub App's permissions are the App's, set at installation.
        let code = device_flow::start(&*transport, &provider, "").map_err(|f| f.to_string())?;
        let cancelled = Arc::new(AtomicBool::new(false));
        {
            let mut flow = self.0.lock().map_err(|e| e.to_string())?;
            flow.cancelled.store(true, Ordering::SeqCst); // a second start replaces the first
            *flow = Flow {
                pending: Some(code.clone()),
                failure: None,
                cancelled: cancelled.clone(),
            };
        }
        let conn = self.clone();
        let waiting = code.clone();
        spawn(Box::new(move || {
            let outcome = device_flow::wait(
                &*transport,
                &provider,
                &waiting,
                &mut std::thread::sleep,
                &unix_now,
                &|| cancelled.load(Ordering::SeqCst),
            );
            conn.finish(outcome, &*store, &cancelled);
        }));
        Ok(code)
    }

    fn finish(
        &self,
        outcome: Result<Tokens, Failure>,
        store: &dyn SecretStore,
        cancelled: &AtomicBool,
    ) {
        if cancelled.load(Ordering::SeqCst) {
            return; // signed out, or replaced by a newer sign-in: this one writes nothing
        }
        let failure = match outcome {
            Ok(tokens) => TokenVault::new(store, VAULT_PREFIX)
                .save(&tokens)
                .err()
                .map(|e| e.to_string()),
            Err(f) => Some(f.to_string()),
        };
        if let Ok(mut flow) = self.0.lock() {
            flow.pending = None;
            flow.failure = failure;
        }
    }

    /// Stops a wait and removes both Keychain items; revoking the App is the person's, in GitHub's settings.
    pub fn sign_out(&self, store: &dyn SecretStore) -> Result<(), String> {
        {
            let mut flow = self.0.lock().map_err(|e| e.to_string())?;
            flow.cancelled.store(true, Ordering::SeqCst);
            *flow = Flow::default();
        }
        TokenVault::new(store, VAULT_PREFIX)
            .sign_out()
            .map_err(|e| e.to_string())
    }

    /// A valid user-to-server token for a GitHub call (refreshed when it is about to expire). Rust side only.
    pub fn access_token(
        &self,
        transport: &dyn Transport,
        store: &dyn SecretStore,
        config: &GithubConfig,
    ) -> Result<Option<String>, String> {
        TokenVault::new(store, VAULT_PREFIX)
            .access_token(transport, &config.provider(), unix_now())
            .map_err(|e| e.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use krizaka_rust::secrets::{Checked, MemorySecrets};
    use serde_json::json;

    /// A mocked GitHub: device endpoint, then the token endpoint's queued answers. No real token anywhere.
    struct FakeGithub(Mutex<Vec<Value>>);
    impl Transport for FakeGithub {
        fn post_form(&self, _url: &str, _fields: &[(&str, &str)]) -> Result<Value, TransportError> {
            Ok(self.0.lock().unwrap().remove(0))
        }
    }

    fn fake(answers: Vec<Value>) -> Arc<FakeGithub> {
        Arc::new(FakeGithub(Mutex::new(answers)))
    }

    fn device() -> Value {
        json!({"device_code": "dc", "user_code": "ABCD-1234", "verification_uri": "https://github.com/login/device", "expires_in": 900, "interval": 1})
    }

    fn store() -> Arc<Checked<MemorySecrets>> {
        Arc::new(Checked(MemorySecrets::default()))
    }

    fn inline(job: Box<dyn FnOnce() + Send>) {
        job();
    }

    #[test]
    fn the_default_config_is_the_public_app_and_a_file_can_replace_it_only_over_https() {
        let c = GithubConfig::default();
        assert_eq!(c.client_id, "Iv23ctICTKzOpI513c8K");
        assert!(c.token_url.starts_with("https://github.com/"));
        let dir = tempfile::tempdir().unwrap();
        let f = dir.path().join("github.json");
        assert_eq!(GithubConfig::load(&f), c);
        fs::write(&f, r#"{"clientId":"Iv-ghe","deviceCodeUrl":"https://ghe.example/login/device/code","tokenUrl":"http://ghe.example/token"}"#).unwrap();
        let l = GithubConfig::load(&f);
        assert_eq!(l.client_id, "Iv-ghe");
        assert_eq!(l.device_code_url, "https://ghe.example/login/device/code");
        assert_eq!(
            l.token_url, c.token_url,
            "a clear-text token endpoint is ignored"
        );
        fs::write(&f, "not json").unwrap();
        assert_eq!(GithubConfig::load(&f), c);
    }

    #[test]
    fn sign_in_shows_the_code_then_stores_the_tokens_in_the_vault_and_the_status_never_holds_a_token(
    ) {
        let conn = Connection::default();
        let s = store();
        let t = fake(vec![
            device(),
            json!({"error": "authorization_pending"}),
            json!({"access_token": "ghu_fake", "refresh_token": "ghr_fake", "expires_in": 28800}),
        ]);
        // `spawn` is held back, so the status can be read while the sign-in waits
        let held: Arc<Mutex<Option<Box<dyn FnOnce() + Send>>>> = Arc::default();
        let h = held.clone();
        let code = conn
            .begin(t, s.clone(), &GithubConfig::default(), move |job| {
                *h.lock().unwrap() = Some(job);
            })
            .unwrap();
        assert_eq!(code.user_code, "ABCD-1234");
        let waiting = conn.status(&*s).unwrap();
        assert!(!waiting.connected);
        assert_eq!(
            waiting.pending.as_ref().unwrap().verification_uri,
            "https://github.com/login/device"
        );
        held.lock().unwrap().take().unwrap()();
        let done = conn.status(&*s).unwrap();
        assert!(done.connected && done.pending.is_none() && done.failure.is_none());
        let json = serde_json::to_string(&done).unwrap();
        assert!(!json.contains("ghu_fake") && !json.contains("ghr_fake") && !json.contains("dc\""));
        assert_eq!(s.get("github.access").unwrap().as_deref(), Some("ghu_fake"));
    }

    #[test]
    fn a_denied_or_expired_sign_in_is_a_failure_to_show_and_stores_nothing() {
        let conn = Connection::default();
        let s = store();
        conn.begin(
            fake(vec![device(), json!({"error": "access_denied"})]),
            s.clone(),
            &GithubConfig::default(),
            inline,
        )
        .unwrap();
        let st = conn.status(&*s).unwrap();
        assert!(!st.connected && st.pending.is_none());
        assert!(st.failure.unwrap().contains("denied"));
        assert_eq!(s.get("github.access").unwrap(), None);
    }

    #[test]
    fn sign_out_removes_both_secrets_and_a_late_approval_writes_nothing() {
        let conn = Connection::default();
        let s = store();
        let held: Arc<Mutex<Option<Box<dyn FnOnce() + Send>>>> = Arc::default();
        let h = held.clone();
        let t = fake(vec![
            device(),
            json!({"access_token": "ghu_late", "refresh_token": "ghr_late", "expires_in": 60}),
        ]);
        conn.begin(t, s.clone(), &GithubConfig::default(), move |job| {
            *h.lock().unwrap() = Some(job);
        })
        .unwrap();
        conn.sign_out(&*s).unwrap();
        held.lock().unwrap().take().unwrap()();
        assert!(!conn.status(&*s).unwrap().connected);
        // connected, then signed out: both items go, and the next call asks to reconnect
        let tokens = Tokens {
            access_token: "a".into(),
            refresh_token: Some("r".into()),
            expires_at: Some(u64::MAX),
        };
        TokenVault::new(&*s, "github").save(&tokens).unwrap();
        conn.sign_out(&*s).unwrap();
        conn.sign_out(&*s).unwrap();
        assert_eq!(
            (
                s.get("github.access").unwrap(),
                s.get("github.refresh").unwrap()
            ),
            (None, None)
        );
        assert_eq!(
            conn.access_token(&*fake(vec![]), &*s, &GithubConfig::default())
                .unwrap(),
            None
        );
    }

    #[test]
    fn an_expiring_token_is_refreshed_before_a_github_call() {
        let conn = Connection::default();
        let s = store();
        TokenVault::new(&*s, "github")
            .save(&Tokens {
                access_token: "old".into(),
                refresh_token: Some("r".into()),
                expires_at: Some(1),
            })
            .unwrap();
        let t = fake(vec![
            json!({"access_token": "new", "refresh_token": "r2", "expires_in": 28800}),
        ]);
        assert_eq!(
            conn.access_token(&*t, &*s, &GithubConfig::default())
                .unwrap()
                .as_deref(),
            Some("new")
        );
        assert_eq!(s.get("github.refresh").unwrap().as_deref(), Some("r2"));
    }
}
