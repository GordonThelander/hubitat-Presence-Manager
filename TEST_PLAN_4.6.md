# 4.6 - live-hub test plan

Covers two fixes from this session, both found after 4.5 was pushed: the advanced diagnostics
tables not rendering correctly on mobile, and Gordon's own presence showing "stale, not counted"
while he was clearly home. Not covered here: anything already shipped in 4.0-4.5 - see git log /
the README beta-scope section for that history.

## Setup

1. Paste `apps/Presence_Manager.groovy` (from this `Claude` folder) into Hubitat's Apps Code
   over the existing Presence Manager app code, Save.
2. **Important - this one needs a manual step**: the "Ignore non-IP evidence after this many
   hours without an event" setting on Advanced Configuration -> Weighting and stale evidence is
   a saved value on your existing install, not just a code default. Raising the default in code
   to 72 does **not** retroactively change what you already have saved. Open that page and set
   it to 72 (or whatever you'd prefer) yourself, then Save.
3. No driver changes this round - nothing to touch in Drivers Code.

---

## Mobile diagnostics table scroll (MOBILE-xx)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| MOBILE-01 | Diagnostics tables scroll instead of wrapping | On a phone (or a narrow browser window), open Advanced Configuration -> Diagnostic Mode | The "People, phone presence and IP decision inputs", "Third Party Services" and "IP status" tables each stay readable - column headers and cell text render as normal horizontal text, not wrapped letter-by-letter - and each table scrolls left/right independently if it's wider than the screen |
| MOBILE-02 | Desktop unaffected (regression) | Open the same Diagnostic Mode page on a desktop browser | Tables render exactly as before - no visible change from 4.5 |
| MOBILE-03 | Other tables unaffected (regression) | Check Occupation Telemetry, Presence Report and Activity Report on mobile | All three still render the same as they did in 4.5 (this fix only touched the three Diagnostic Mode tables) |

## Stale-evidence threshold (STALETHRESH-xx)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| STALETHRESH-01 | New threshold takes effect | After manually updating the setting to 72 per the Setup step above, check Diagnostic Mode | No immediate change expected unless someone was already sitting past the old 12-hour mark - if Gordon's row was showing "stale, not counted" before this update, it should clear back to a normal green "Home" once the setting is saved (no new event needed, since the comparison itself changes) |
| STALETHRESH-02 | Passive - normal continuous presence | Over the next few days, with no special action, watch whether anyone's presence row flags "stale, not counted" during an ordinary stretch of staying home (evenings, a weekend) | Should not trigger under normal conditions anymore - flagging should now only appear if a presence sensor genuinely produces no event for 3+ days straight |
| STALETHRESH-03 | Genuinely stale case still catches (regression) | No action needed - this is the same untestable-on-demand case as the original STALE-01 test, just at a longer horizon now | If a presence sensor ever does go silent for 3+ days while still reporting a stale "present" value, the amber flag should still appear - the mechanism itself hasn't changed, only the default number |

---

Nothing here is a hard blocker before pushing - MOBILE-01/02/03 and STALETHRESH-01 are the ones
I'd actually want confirmed first since they're checkable in one sitting. STALETHRESH-02/03 are
things to watch over the following days regardless of when this gets pushed. Reminder: last
time, Overall Status stayed correct throughout (via your wife's evidence) even while Gordon's
own row was misleadingly flagged - so this fix is about display/exclusion accuracy, not a
household-level false-Away bug.
