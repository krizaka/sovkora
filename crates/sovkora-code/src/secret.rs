//! A credential in memory: the engine's bearer, a GitHub token.
//!
//! `Secret` has no `Display`, no `Debug` and no `Serialize`, so a `{}`, a `{:?}`, a log line, a panic message or a JSON
//! body cannot carry it by accident. The value leaves only through `expose`, which the one adapter that puts it on the
//! wire calls.

/// A credential that never prints and never serialises.
pub struct Secret(String);

impl Secret {
    pub fn new(value: impl Into<String>) -> Secret {
        Secret(value.into())
    }

    /// The value, for the header that carries it. Nothing else should call this.
    pub fn expose(&self) -> &str {
        &self.0
    }
}

#[cfg(test)]
mod tests {
    use super::Secret;
    use std::marker::PhantomData;

    /// Autoref probes: the method on `Probe<T>` itself wins when `T` has the trait, the one on `&Probe<T>` otherwise.
    struct Probe<T>(PhantomData<T>);

    macro_rules! implements {
        ($ty:ty, $bound:path) => {{
            trait Yes {
                fn has(&self) -> bool {
                    true
                }
            }
            impl<T: $bound> Yes for Probe<T> {}
            trait No {
                fn has(&self) -> bool {
                    false
                }
            }
            impl<T> No for &Probe<T> {}
            // both traits named once, so the one resolution does not pick is not dead code (String has every bound)
            let _ = (<Probe<String> as Yes>::has, <&Probe<String> as No>::has);
            (&Probe::<$ty>(PhantomData)).has()
        }};
    }

    #[test]
    fn the_probe_sees_a_trait_a_type_has() {
        assert!(implements!(String, std::fmt::Debug));
        assert!(implements!(String, serde::Serialize));
    }

    #[test]
    fn a_secret_cannot_be_displayed_debugged_or_serialised() {
        assert!(!implements!(Secret, std::fmt::Display));
        assert!(!implements!(Secret, std::fmt::Debug));
        assert!(!implements!(Secret, serde::Serialize));
        assert!(
            !implements!(Secret, Clone),
            "one owner: a copy is one more place it can leak from"
        );
    }

    #[test]
    fn the_value_is_given_back_only_on_purpose() {
        assert_eq!(Secret::new("v").expose(), "v");
    }
}
