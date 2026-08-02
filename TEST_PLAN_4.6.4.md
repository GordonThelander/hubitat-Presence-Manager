# 4.6.4 - live-hub test plan

Covers the Presence Report CSV export, fixed twice over now. 4.6.3 switched it from `hh:mm` to
decimal hours to dodge spreadsheet apps auto-reformatting time-shaped text, but that changed the
format away from what was actually wanted. This round keeps `hh:mm` exactly as shown on the page,
forced literal via a spreadsheet-formula cell so Excel/Sheets/Numbers stop auto-typing it. Not
covered here: the 4.6.3 mobile letter-wrapping fix (Decision detail table) - assumed still good
unless you see otherwise, flag it if so. See git log / README beta-scope for everything else.

## Setup

1. Paste `apps/Presence_Manager.groovy` (from this `Claude` folder) into Hubitat's Apps Code
   over the existing Presence Manager app code, Save.
2. No settings to change, no driver changes.

---

## Presence Report CSV - hh:mm as literal text (CSV2-xx)

Each hour cell and the Date column now contain a tiny formula (`="07:00"`, `="2026-08-01"`)
instead of a plain value - a standard, widely-supported trick that makes Excel/Sheets/Numbers
display and copy the cell as exactly that text, rather than auto-detecting it as a time or date
and reformatting it. Selecting one of these cells will show the `="..."` formula in the formula
bar rather than plain text - that's expected, not a bug; the displayed/copied value is still just
the plain text.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| CSV2-01 | Hour values show hh:mm | Download the CSV from Presence Report, open in your spreadsheet app | Hour cells show `07:00`, `24:00`, etc. - matching the on-page table exactly, no trailing seconds, no decimals |
| CSV2-02 | Date column no longer shows #### | Look at the Date column | Full date text visible (or normally truncated by column width like any text, not `####`) - widening the column shows the exact same string as the on-page table's Date column, not a reformatted date |
| CSV2-03 | Values copy/paste as plain text | Select an hour cell, copy it, paste into another cell or a text field | Pastes as the plain text (e.g. `07:00`), not the formula |
| CSV2-04 | On-page table unaffected (regression) | Look at the Presence Report table itself | Unchanged - only the CSV file's cell format changed |

---

Nothing here is a hard blocker before pushing - CSV2-01 and CSV2-02 are the ones that actually
matter, the rest is just confirming the mechanism behaves as expected.
