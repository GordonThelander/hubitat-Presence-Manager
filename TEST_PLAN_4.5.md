# 4.5 - live-hub test plan

Covers three fixes from this session: the IP ping scheduling watchdog, a matching watchdog for
stuck Guest Mode expiry (found by auditing every other `runIn` chain in the app for the same
failure class once the ping issue turned up), and stale-evidence flagging on the dashboard's raw
signal columns. Not covered here: anything already shipped in 4.0-4.4 - see git log / the README
beta-scope section for that history.

## Setup

1. Paste `apps/Presence_Manager.groovy` (from this `Claude` folder) into Hubitat's Apps Code
   over the existing Presence Manager app code, Save.
2. Unlike some of your other apps, Presence Manager doesn't display a version string anywhere
   in its own UI - a clean Save with no red error banner is the on-hub confirmation that it
   loaded, not a page subtitle to check.
3. No driver changes this round - nothing to touch in Drivers Code.

---

## Regression: normal operation unaffected

| # | Test | Steps | Expected |
|---|------|-------|----------|
| SANITY-01 | Dashboard still renders | Open the main page | Overall Status, Occupation Telemetry, Location Lookup (if enabled) all render normally, no errors |
| SANITY-02 | Fresh evidence shows no stale flag | With Gordon and Tanya both currently home and reachable | Their "Hubitat / 3rd Party Presence" and "LAN IP" columns show plain green "Home"/"present" - not the new amber "stale, not counted" wording |
| SANITY-03 | Normal ping scheduling continues | Wait one full ping interval (2 min per your config), then check Advanced Configuration -> Diagnostic Mode -> "Pending schedule" / "Last ping scheduled" | Shows a normal "Next IP check scheduled in X seconds" message - no "watchdog triggered a recovery sweep" text |
| SANITY-04 | Watchdog stays silent under healthy conditions | Leave the app running normally for 15-20 minutes | No spurious recovery-sweep messages appear in "Last ping scheduled" during that window |

## Ping watchdog (PING-xx)

The hard part: this only matters when Hubitat's scheduler actually drops a `runIn` callback,
which can't be triggered on demand. There's no clean way to force that from outside the hub, so
this is mostly a "watch and see" test rather than something you can tick off in one sitting.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| PING-01 | Passive observation | Do nothing differently - just keep an eye on the Activity Report over the next several mornings around 5am, the pattern you'd been seeing | Either the false-Departed blip stops needing a manual refresh to clear (self-corrects within roughly 1-3 minutes on its own), or - if it still happens - "Last ping scheduled" shows the new "watchdog triggered a recovery sweep" message around the same time, which at least confirms the watchdog fired even if it didn't fully prevent the symptom |
| PING-02 (optional) | Scheduled-jobs inspection | If your hub's UI exposes a "scheduled jobs" view for this app (varies by platform version - I'm not certain of the exact path on yours), check whether a `runPingChecks` job is listed and roughly matches your configured interval | Confirms scheduling is active day-to-day, independent of whether it's ever actually dropped a beat |

## Guest Mode expiry watchdog (GUEST-xx)

Same problem as ping: only matters if Hubitat drops the `expireGuestMode` callback, which can't
be forced on demand. The minimum Guest Mode duration is 1 hour, so this is a slower test than
the others regardless.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| GUEST-01 | Normal expiry still works (regression) | Start Guest Mode with the 1-hour minimum, then leave it alone until it's due to end | Guest Mode ends on time on its own, exactly as before - confirms the new watchdog doesn't interfere with the normal on-time path |
| GUEST-02 | Passive observation | If Guest Mode ever appears to still show active noticeably past the time it should have ended | Check "Last decision" on Advanced Configuration -> Diagnostic Mode for a "Guest Mode expiry was overdue - watchdog forced expiry" message - if present, it caught a real stuck case; if Guest Mode simply never gets stuck, there's nothing to see here and that's fine |

## Stale evidence display (STALE-xx)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| STALE-01 (~1hr commitment) | Force a stale presence reading | Advanced Configuration -> Weighting and stale evidence -> temporarily lower "Ignore non-IP evidence after this many hours without an event" to 1. Then leave your phone stationary at home - no lock/unlock/notification interactions that would refresh the presence sensor - for over an hour | After ~1hr+, your row's presence column should switch to amber "Home (stale, not counted)", and Effective Status should drop to reflect the exclusion |
| STALE-02 | Recovery and cleanup | After STALE-01, unlock/use the phone once (fires a fresh presence event), then set "Ignore non-IP evidence..." back to your normal value (24) | Amber label clears back to normal green "Home" once fresh evidence arrives; setting is back to how you had it |
| STALE-03 | Passive - IP staleness path | No action needed, just glance at the LAN IP column during normal operation over the next few days | Should always show either plain "present" (fresh) or red "unreachable" - the amber "reachable (stale, not counted)" wording should only appear during the same conditions as PING-01, giving you a second, independent confirmation signal for that scenario when it happens |

---

Nothing here is a hard blocker before pushing - SANITY-01 through 04 are the ones I'd actually
want confirmed first (they prove the change is safe under normal conditions), PING-01/STALE-03
are things to watch over the following days regardless of when this gets pushed.
