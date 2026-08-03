# 4.6.5 - live-hub test plan

Covers two small fixes: a Version line added to Advanced Configuration, and the Third Party
Services picker's description text colliding with its device-picker button. Not covered here:
the CSV/mobile-scroll history from 4.6.1-4.6.4 - see git log / README beta-scope for that.

## Setup

1. Paste `apps/Presence_Manager.groovy` (from this `Claude` folder) into Hubitat's Apps Code
   over the existing Presence Manager app code, Save.
2. No settings to change, no driver changes.

---

## Version display (VER-xx)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| VER-01 | Version shows on Advanced Configuration | Open Advanced Configuration | A blue "Version 4.6.5" line appears just below the page's own "Advanced Configuration" heading, above the Navigation section |
| VER-02 | Not shown elsewhere (regression) | Check the main dashboard and other pages | No version text added anywhere else - this was only requested for Advanced Configuration |

## Third Party Services picker overlap (TPS-xx)

The description text used to live in the input's own `description:` field, which Hubitat renders
as part of the same native widget as the device-picker button - no way to insert spacing inside
that from app code. Moved the guidance text into its own `paragraph()` before the input instead,
which guarantees no overlap since it's a separate element in normal page flow, not fighting for
space inside the widget.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| TPS-01 | No more overlap | Open Advanced Configuration -> Third Party Services config | The explanatory text ("Optional whole-house or system-level presence evidence... on = home evidence, off = departed/away evidence...") appears as normal paragraph text above the "Third Party Services switches" picker - nothing overlapping the button |
| TPS-02 | Picker still works (regression) | Tap the device-picker button, select/deselect a device | Works exactly as before - only the guidance text's position and styling changed (now plain paragraph text, not the native blue hint style), not the input itself |
| TPS-03 | Existing selections preserved (regression) | If you already have Third Party Services devices configured | Still shows configured after pasting the new code and saving - this change didn't touch the `houseEvidenceSwitches` setting itself |

---

Nothing here is a hard blocker before pushing - both are cosmetic/informational, checkable in one
sitting.
