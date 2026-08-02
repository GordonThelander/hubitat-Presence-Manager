# 4.6.3 - live-hub test plan

Covers two fixes found after 4.6.2 was tested live: the Decision detail table on the Diagnostic
Mode page still wrapping text letter by letter on mobile, and the Presence Report's CSV export
showing a spurious trailing `:00` on hour values when opened in a spreadsheet app. Not covered
here: anything already shipped in 4.0-4.6.2 - see git log / the README beta-scope section.

## Setup

1. Paste `apps/Presence_Manager.groovy` (from this `Claude` folder) into Hubitat's Apps Code
   over the existing Presence Manager app code, Save.
2. No settings to change this round, no driver changes.

---

## Mobile letter-wrapping - the real root cause (WRAP-xx)

4.6.1/4.6.2 fixed three tables by switching their CSS class, but the "Decision detail" table at
the top of Diagnostic Mode uses a different class (`pm-kv-table`) that was never touched and had
the same underlying flaw: `overflow-wrap:anywhere` (combined with `word-break:break-word`) tells
the browser it's free to break any word at any character, which changes how table auto-layout
computes a column's *minimum* width - letting it collapse a column down to almost nothing and
then wrap ordinary short text (like "occupied") letter by letter to fit. This round removes both
properties everywhere they appeared and uses `overflow-wrap:break-word` instead, which only
breaks a word as a last resort and doesn't collapse the column's minimum width the same way -
should fix Decision detail without reintroducing the original un-scrollable/garbled table problem
for the tables holding genuinely long freeform text (Activity Report messages, evidence lists).

| # | Test | Steps | Expected |
|---|------|-------|----------|
| WRAP-01 | Decision detail renders correctly | On a phone, open Advanced Configuration -> Diagnostic Mode, look at the "Decision detail" table at the top | Short values like "occupied" render as a single line, not one letter per line. Longer values (e.g. "Last decision") wrap normally at word boundaries, not mid-word |
| WRAP-02 | Still scrolls if needed | If any row's value is wide enough to not fit the screen even wrapped normally | The table (or its row) scrolls horizontally rather than the page cutting it off |
| WRAP-03 | The three previously-fixed tables still fine (regression) | Check "People, phone presence and IP decision inputs", "Third Party Services" and "IP status" on the same page | Still render and scroll the same as after 4.6.1/4.6.2 - this change didn't touch their CSS class, only the wrap-style one |
| WRAP-04 | Activity Report still wraps sensibly (regression) | Open Activity Report on mobile | Long detail messages still wrap at word boundaries like before, not forced onto one huge unbroken line - confirms the fix didn't overcorrect into the opposite problem |
| WRAP-05 | If still broken anywhere | N/A | Please screenshot which specific table/row and whether it's letter-wrapping again or something else - if this specific fix (dropping `overflow-wrap:anywhere`) doesn't resolve it, the cause is something other than what's been diagnosed so far and needs a different look |

## Presence Report CSV - decimal hours instead of hh:mm (CSVFIX-xx)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| CSVFIX-01 | No more spurious :00 | Download the CSV again from Presence Report, open in your spreadsheet app | Hour values show as plain decimals (e.g. `24.00`, `6.50`, `121.75`) - no colons, nothing auto-reformatted |
| CSVFIX-02 | Values match the on-page table | Compare a few rows against the Presence Report table on-screen | Decimal value = on-page hh:mm converted to hours (6:30 -> 6.50, 24:00 -> 24.00) |
| CSVFIX-03 | On-page table unaffected (regression) | Look at the Presence Report table itself, not the CSV | Still displays hh:mm as before - only the CSV export format changed |

---

Nothing here is a hard blocker before pushing - WRAP-01 through 04 and CSVFIX-01/02 are the ones
I'd actually want confirmed first since they're checkable in one sitting.
