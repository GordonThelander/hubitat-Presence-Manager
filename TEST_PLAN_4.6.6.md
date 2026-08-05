# 4.6.6 - live-hub test plan

Covers a real reliability bug found from the "Tanya was away 07:00-16:30 and nothing logged,
my own shop trip wasn't logged either" report: the evidence watchdog itself (built in 4.5 to
catch a stuck IP ping schedule and stuck Guest Mode expiry) was a self-rescheduling `runIn` chain
- the exact single-point-of-failure pattern it existed to protect against. If Hubitat ever
dropped that one callback, the watchdog died silently, and with it the periodic re-evaluation
that would otherwise catch a person's status change even without a fresh device event - matching
roughly 44 hours of total silence in the Activity Report despite real comings and goings. Also
covers 4.6.5's two fixes (Version display, Third Party Services picker overlap), which hadn't
been confirmed on-hub yet before this report came in.

## Setup

1. Paste `apps/Presence_Manager.groovy` (from this `Claude` folder) into Hubitat's Apps Code
   over the existing Presence Manager app code, Save.
2. No settings to change, no driver changes.

---

## Evidence watchdog reliability (WATCHDOG-xx)

The hard part, same as the original ping watchdog: this only matters when Hubitat's scheduler
actually drops a callback, which can't be triggered on demand. Most of this is "watch and see"
rather than something to tick off in one sitting.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| WATCHDOG-01 | New diagnostic row appears | Open Advanced Configuration -> Diagnostic Mode -> Decision detail | A "Last ping scheduled" row now appears (previously existed in state but was never shown - "Pending schedule" is a different, unrelated field) |
| WATCHDOG-02 | Normal operation unaffected | Open the main dashboard, check Occupation Telemetry | Renders normally, current status for everyone matches reality |
| WATCHDOG-03 | Passive - the actual fix | Over the next several days, don't do anything special - just use the house normally | Every genuine arrival/departure for every configured person should show up in the Activity Report and Presence Report, even across long gaps with no manual interaction with the app. If a multi-hour or multi-day gap in logged events ever reappears despite real activity, that means this fix didn't fully close the gap and needs another look |
| WATCHDOG-04 | Optional cross-check | If your hub's UI exposes a scheduled-jobs view for this app | `evaluateEvidenceWatchdog` should appear as a recurring job (not a one-shot), confirming it's now platform-managed rather than a runIn chain |

## 4.6.5 fixes, not yet confirmed (CARRYOVER-xx)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| CARRYOVER-01 | Version display | Open Advanced Configuration | A blue "Version 4.6.6" line appears just below the page heading |
| CARRYOVER-02 | Third Party Services picker | Open Advanced Configuration -> Third Party Services config | Guidance text appears above the "Third Party Services switches" picker, not overlapping the button |

---

WATCHDOG-01/02 and CARRYOVER-01/02 are checkable in one sitting. WATCHDOG-03 is the one that
actually matters and can only be confirmed by continuing to use the house normally over the
coming days - please flag it immediately if any gap in logged activity reappears, since that
would mean there's still a failure mode this fix didn't cover.
