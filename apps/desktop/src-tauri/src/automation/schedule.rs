//! When a scheduled automation is due — computed on the device, in local time, so it works offline and no server has
//! to wake a sleeping Mac (ADR-079 §2).
//!
//! Times are local *naive* date-times: "08:00" means 08:00 on the Mac's clock, whatever the zone. On a DST change the
//! slot that does not exist (02:30 on the spring-forward night) simply runs at the next tick after it.

use super::types::{CatchUp, Schedule};
use time::format_description::BorrowedFormatItem;
use time::macros::format_description;
use time::{Date, Duration, PrimitiveDateTime, Time};

/// A local wall-clock date-time with no zone: the scheduler's one notion of "when".
pub type LocalDateTime = PrimitiveDateTime;

/// How late a slot may still run under `CatchUp::Skip`. A fact of the scheduler's tick, not a business value.
// @fixed: the scheduler ticks every 20 s; a slot older than this was missed, not late.
pub const GRACE_MINUTES: i64 = 15;

const SLOT_FORMAT: &[BorrowedFormatItem<'static>] =
    format_description!("[year]-[month]-[day]T[hour]:[minute]:[second]");
const DAY_FORMAT: &[BorrowedFormatItem<'static>] = format_description!("[year]-[month]-[day]");
const TIME_OF_DAY_FORMAT: &[BorrowedFormatItem<'static>] = format_description!("[hour]:[minute]");
const TIME_OF_DAY_LENIENT: &[BorrowedFormatItem<'static>] =
    format_description!("[hour padding:none]:[minute padding:none]");

/// `2026-10-16T08:00:00`, the form slots and audit stamps are stored in.
pub fn format_slot(t: LocalDateTime) -> String {
    t.format(SLOT_FORMAT).unwrap_or_default()
}

/// `2026-10-16`.
pub fn format_day(t: LocalDateTime) -> String {
    t.format(DAY_FORMAT).unwrap_or_default()
}

/// `08:00`, the form a schedule's time of day is written in.
pub fn format_time_of_day(t: LocalDateTime) -> String {
    t.format(TIME_OF_DAY_FORMAT).unwrap_or_default()
}

pub fn parse_time(time: &str) -> Result<Time, String> {
    Time::parse(time, TIME_OF_DAY_LENIENT)
        .map_err(|_| format!("invalid time of day: {time} (expected HH:MM)"))
}

pub fn validate(schedule: &Schedule) -> Result<(), String> {
    parse_time(&schedule.time)?;
    if let Some(d) = schedule.days.iter().find(|d| !(1..=7).contains(*d)) {
        return Err(format!("invalid weekday: {d} (1 = Monday … 7 = Sunday)"));
    }
    Ok(())
}

fn runs_on(schedule: &Schedule, day: Date) -> bool {
    schedule.days.is_empty()
        || schedule
            .days
            .contains(&(day.weekday().number_from_monday()))
}

/// The most recent slot at or before `now` (looking back at most a week), if any.
pub fn latest_slot(schedule: &Schedule, now: LocalDateTime) -> Option<LocalDateTime> {
    let time = parse_time(&schedule.time).ok()?;
    (0..8).find_map(|back| {
        let day = now.date() - Duration::days(back);
        let slot = PrimitiveDateTime::new(day, time);
        (slot <= now && runs_on(schedule, day)).then_some(slot)
    })
}

/// The next slot strictly after `now`.
pub fn next_slot(schedule: &Schedule, now: LocalDateTime) -> Option<LocalDateTime> {
    let time = parse_time(&schedule.time).ok()?;
    (0..9).find_map(|ahead| {
        let day = now.date() + Duration::days(ahead);
        let slot = PrimitiveDateTime::new(day, time);
        (slot > now && runs_on(schedule, day)).then_some(slot)
    })
}

/// The slot to run now, if one is due: the latest slot, not handled yet, not older than the automation itself, and —
/// under `CatchUp::Skip` — not older than the grace window.
pub fn due_slot(
    schedule: &Schedule,
    created_at: LocalDateTime,
    last_slot: Option<LocalDateTime>,
    now: LocalDateTime,
) -> Option<LocalDateTime> {
    let slot = latest_slot(schedule, now)?;
    if slot < created_at {
        return None;
    }
    if last_slot.is_some_and(|last| slot <= last) {
        return None;
    }
    if schedule.catch_up == CatchUp::Skip && now - slot > Duration::minutes(GRACE_MINUTES) {
        return None;
    }
    Some(slot)
}

pub fn parse_slot(s: &str) -> Option<LocalDateTime> {
    PrimitiveDateTime::parse(s, SLOT_FORMAT).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn at(s: &str) -> LocalDateTime {
        match PrimitiveDateTime::parse(
            s,
            format_description!("[year]-[month]-[day] [hour]:[minute]"),
        ) {
            Ok(t) => t,
            Err(e) => panic!("bad test instant {s:?}: {e}"),
        }
    }
    fn daily(time: &str) -> Schedule {
        Schedule {
            time: time.into(),
            days: vec![],
            catch_up: CatchUp::Once,
        }
    }

    #[test]
    fn every_morning_at_eight_is_due_once_after_eight() {
        let s = daily("08:00");
        let created = at("2026-10-15 12:00");
        assert_eq!(due_slot(&s, created, None, at("2026-10-16 07:59")), None);
        assert_eq!(
            due_slot(&s, created, None, at("2026-10-16 08:00")),
            Some(at("2026-10-16 08:00"))
        );
        // handled: not due again the same day
        assert_eq!(
            due_slot(
                &s,
                created,
                Some(at("2026-10-16 08:00")),
                at("2026-10-16 18:00")
            ),
            None
        );
        // due the next morning
        assert_eq!(
            due_slot(
                &s,
                created,
                Some(at("2026-10-16 08:00")),
                at("2026-10-17 08:00")
            ),
            Some(at("2026-10-17 08:00"))
        );
    }

    #[test]
    fn a_slot_before_the_automation_existed_never_runs() {
        let s = daily("08:00");
        assert_eq!(
            due_slot(&s, at("2026-10-16 09:00"), None, at("2026-10-16 10:00")),
            None
        );
    }

    #[test]
    fn catch_up_once_runs_the_latest_missed_slot_only() {
        let s = daily("08:00");
        // The Mac slept from Friday evening to Monday 10:00: one run, for Monday 08:00.
        let due = due_slot(
            &s,
            at("2026-10-01 00:00"),
            Some(at("2026-10-16 08:00")),
            at("2026-10-19 10:00"),
        );
        assert_eq!(due, Some(at("2026-10-19 08:00")));
    }

    #[test]
    fn catch_up_skip_drops_a_slot_older_than_the_grace_window() {
        let s = Schedule {
            catch_up: CatchUp::Skip,
            ..daily("08:00")
        };
        let created = at("2026-10-01 00:00");
        assert_eq!(
            due_slot(&s, created, None, at("2026-10-16 08:10")),
            Some(at("2026-10-16 08:00"))
        );
        assert_eq!(due_slot(&s, created, None, at("2026-10-16 09:00")), None);
    }

    #[test]
    fn weekdays_only() {
        // 2026-10-17 is a Saturday.
        let s = Schedule {
            days: vec![1, 2, 3, 4, 5],
            ..daily("08:00")
        };
        assert_eq!(
            latest_slot(&s, at("2026-10-17 09:00")),
            Some(at("2026-10-16 08:00"))
        );
        assert_eq!(
            next_slot(&s, at("2026-10-17 09:00")),
            Some(at("2026-10-19 08:00"))
        );
    }

    #[test]
    fn validation_refuses_bad_times_and_days() {
        assert!(validate(&daily("8h")).is_err());
        assert!(validate(&daily("24:00")).is_err());
        assert!(validate(&Schedule {
            days: vec![0],
            ..daily("08:00")
        })
        .is_err());
        assert!(validate(&daily("07:30")).is_ok());
    }

    #[test]
    fn stored_forms_round_trip_and_keep_their_shape() {
        let t = at("2026-10-16 08:05");
        assert_eq!(format_slot(t), "2026-10-16T08:05:00");
        assert_eq!(parse_slot("2026-10-16T08:05:00"), Some(t));
        assert_eq!(format_day(t), "2026-10-16");
        assert_eq!(format_time_of_day(t), "08:05");
        assert_eq!(parse_slot("2026-10-16 08:05:00"), None);
        assert_eq!(parse_slot("nonsense"), None);
    }

    #[test]
    fn a_time_of_day_without_its_leading_zero_is_still_read() {
        let short = parse_time("8:00").ok();
        assert!(short.is_some());
        assert_eq!(short, parse_time("08:00").ok());
        assert!(parse_time("08:60").is_err());
    }
}
