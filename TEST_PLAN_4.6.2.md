# 4.6.2 - live-hub test plan

Covers everything from this session since 4.5 was pushed: the advanced diagnostics tables not
rendering correctly on mobile (two fix attempts now - the first only partly worked), Gordon's own
presence showing "stale, not counted" while he was clearly home (fixed by raising the
stale-evidence threshold, settled on 48 hours), widening the Presence Report from 30 to 90 days,
and a CSV export link under the Presence Report table. Not covered here: anything already shipped
in 4.0-4.5 - see git log / the README beta-scope section for that history.

## Setup

1. Paste `apps/Presence_Manager.groovy` (from this `Claude` folder) into Hubitat's Apps Code
   over the existing Presence Manager app code, Save.
2. **One manual step this round** - raising a code default does not retroactively change a value
   you already have saved on an existing install: Advanced Configuration -> Weighting and stale
   evidence -> set "Ignore non-IP evidence after this many hours without an event" to 48.
   (The Activity Report retention cap stays at 500 - not changed this round.)
3. No driver changes this round - nothing to touch in Drivers Code.

---

## Mobile diagnostics table scroll - second attempt (MOBILE-xx)

The first attempt (4.6.1, swapping to a nowrap CSS class) fixed the garbled letter-by-letter text
but the tables still didn't actually scroll. This round adds `min-width:0` to the wrapping div -
a well-known CSS fix for the case where a flex/grid ancestor stops a nested `overflow-x:auto` div
from ever becoming the thing that scrolls, growing itself instead. I can't confirm Hubitat's page
layout actually uses flex/grid there without seeing it live, so this is a reasoned attempt, not a
confirmed fix.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| MOBILE-01 | Diagnostics tables actually scroll | On a phone, open Advanced Configuration -> Diagnostic Mode, try swiping left/right on the "People, phone presence and IP decision inputs" table | The table itself scrolls horizontally within its own box - the rest of the page (labels, headings) stays put |
| MOBILE-02 (if MOBILE-01 still fails) | Diagnose what's actually happening | Try the swipe again and note exactly what happens | Tell me one of: (a) the *whole page* shifts sideways when you swipe (confirms the flex/grid theory, points to a different fix), (b) nothing happens at all - no movement in either the table or the page (suggests Hubitat's mobile app WebView may be blocking horizontal touch gestures outright, which would need a different approach entirely), or (c) something else - describe it |
| MOBILE-03 | Desktop unaffected (regression) | Open the same Diagnostic Mode page on a desktop browser | Tables render exactly as before |
| MOBILE-04 | Other tables unaffected (regression) | Check Occupation Telemetry, Presence Report and Activity Report on mobile | All render the same as before - this fix only touched shared wrapper CSS, but it's shared by every table so worth a quick look |

## Stale-evidence threshold (STALETHRESH-xx)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| STALETHRESH-01 | New threshold takes effect | After manually updating the setting to 48 per the Setup step above, check Diagnostic Mode | No immediate change expected unless someone was already sitting past the old 12-hour mark - if Gordon's row was showing "stale, not counted" before this update, it should clear back to a normal green "Home" once the setting is saved (no new event needed, since the comparison itself changes) |
| STALETHRESH-02 | Passive - normal continuous presence | Over the next few days, with no special action, watch whether anyone's presence row flags "stale, not counted" during an ordinary stretch of staying home (evenings, a weekend) | Should not trigger under normal conditions anymore - flagging should now only appear if a presence sensor genuinely produces no event for 2+ days straight |
| STALETHRESH-03 | Genuinely stale case still catches (regression) | No action needed - this is the same untestable-on-demand case as the original STALE-01 test, just at a longer horizon now | If a presence sensor ever does go silent for 2+ days while still reporting a stale "present" value, the amber flag should still appear - the mechanism itself hasn't changed, only the default number |

## Presence Report - 90 day window (HISTORY-xx)

The Presence Report reconstructs each person's Home hours from Activity Report rows. The
Activity Report retention cap stays at 500 rows this round (not raised, per Gordon), so the
90-day window is a ceiling, not a guarantee - how far back it actually fills in still depends on
how many rows 500 covers for your household's activity level, same as it always has.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| HISTORY-01 | Presence Report header shows 90 days | Open Presence Report | Section header reads "Rolling 90 day presence report" |
| HISTORY-02 | Table content unaffected (regression) | Check the actual rows shown | Same data as before - the window is wider but actual coverage is still bounded by existing history, so you likely won't see a visible difference immediately |

## Presence Report - CSV export (CSV-xx)

New "Download CSV" link under the Presence Report table. This uses a `data:` URI embedded
directly in the page rather than a Hubitat OAuth/mappings endpoint (simpler, no extra hub
configuration needed) - but whether Hubitat's page renderer and your browser/app both handle a
`data:` link with a `download` attribute correctly is genuinely untested on-hub.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| CSV-01 | Link appears | Open Presence Report | A "Download CSV" link appears just below the table |
| CSV-02 | Download works | Tap/click the link | A file named `presence_report.csv` downloads or offers to save. If nothing happens, or it opens as a page of text instead of downloading, or errors - let me know exactly what you see, since that determines whether this needs the more involved OAuth-endpoint approach instead |
| CSV-03 | CSV content is correct | Open the downloaded file in a spreadsheet or text editor | One row per visible day plus a Total row, one column per person, values matching what's shown in the on-page table (hh:mm format) |

---

Nothing here is a hard blocker before pushing - MOBILE-01, STALETHRESH-01, HISTORY-01 and CSV-01/02/03
are the ones I'd actually want confirmed first since they're checkable in one sitting.
STALETHRESH-02/03 are things to watch over the following days regardless of when this gets pushed.
Reminder: Overall Status stayed correct throughout the original stale-flag bug (via your wife's
evidence) even while Gordon's own row was misleadingly flagged - so that fix is about
display/exclusion accuracy, not a household-level false-Away bug.
