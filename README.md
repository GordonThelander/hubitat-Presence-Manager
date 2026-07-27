# Presence Manager

**4.3.3 Beta - Hubitat Elevation household occupancy manager**

Presence Manager combines multiple presence signals into one reliable household status for Hubitat. It is designed to avoid a false `Away` result when one phone, integration or network check briefly drops out while other evidence still shows someone is home.

## What it does

| Capability | Behaviour |
|---|---|
| Person-based presence | Tracks up to 10 named people using Hubitat mobile geolocation presence, an optional single local IP address per person, or both. |
| Third-party services | Accepts virtual switches or compatible inputs from Google Home, Alexa, SmartThings and similar integrations as additional household evidence. |
| Conservative departure logic | Treats positive evidence as Home immediately, but applies configurable failure thresholds and departure delays before committing Away. |
| Guest Mode | Keeps the household occupied for a timed period when visitors are present, even when normal person evidence is absent. |
| Unified output | Drives an app-created child device or an existing writable switch / presence device. The child output presents switch, presence, contact and motion states for broad Hubitat compatibility. |
| Operations and diagnostics | Provides an activity report, manual refresh, configurable notifications, force Home/Away controls, evidence diagnostics and mobile-safe dashboards. |

## Decision model

Presence Manager is **fast to Home and conservative to Away**:

- Any credible positive person, service or Guest Mode evidence holds the household state as occupied.
- A phone IP check is useful as local confirmation, not as a sole authoritative proof of absence.
- Configurable arrival and departure delays reduce flapping from brief geolocation, Wi-Fi or integration inconsistencies.
- The app rechecks live evidence before committing an Away state, including when an external output device changes.

## Package contents

| File | Purpose |
|---|---|
| `apps/Presence_Manager.groovy` | The Presence Manager application, configuration screens, evaluation engine, dashboard, reporting and notifications. |
| `drivers/Presence_Manager_Output.groovy` | Main output child driver. Exposes switch, presence, contact and motion capabilities. |
| `drivers/Presence_Manager_Guest_Mode_Switch.groovy` | Managed child switch used for Guest Mode state. |
| `packageManifest.json` | Hubitat Package Manager manifest for GitHub-hosted installation. |

## Installation

1. Install both child drivers first: **Presence Manager Output** and **Presence Manager Guest Mode Switch**.
2. Install the **Presence Manager** app.
3. Open the app and configure the main household status target.
4. Add people, optional phone IP addresses and optional Hubitat mobile presence sensors.
5. Configure Third Party Services, notifications, delays and evidence weights as required.
6. Test with the dashboard's **Refresh data**, **Evaluate now** and **Test Notification** controls before relying on automations.

Once this repository is published, install through Hubitat Package Manager using the repository's `packageManifest.json`, or use Hubitat's manual code installation flow.

## 4.3.3 beta scope

4.1 fixed an Activity Report defect: deleting a person could leave the report keyed to the person that slid into the deleted slot's old data, so it could log a Home/Departed transition for the wrong person. It also hardened the report so two people sharing a display name cannot suppress each other's status rows.

4.2 is a consistency and cleanup pass on top of that: the main-status-switch assignment screen (Main page setup gate, Application Child Switch Configuration page, and Advanced Configuration page) is now a single shared block instead of three copy-pasted ones, which also fixes the Application Child Switch Configuration page silently hiding its "ready" confirmation when the main status switch was assigned to an existing switch or presence device rather than the managed child switch. The "Last N report events" labels now reflect the configurable retention setting instead of a hardcoded 500, and a couple of small dead-code and clarity cleanups were made.

4.3 adds a new Presence Report page, linked from the main dashboard just below Activity Report, showing each user's hours present per calendar day over a rolling 30 day window plus a 30-day total row. It's built entirely on the existing Activity Report data, no new tracking was added. Known limitation: a report window spanning a person deletion can briefly misattribute hours to whoever takes over that slot, the same root cause as the defect fixed in 4.1, just not retroactive to already-stored history rows.

4.3.1 fixes a bug in that report: anyone who had been continuously Home since before updating to 4.3 showed 00:00 indefinitely, because their only recorded Home event predated the new tracking field 4.3 introduced and was being silently skipped. Older history rows are now read via a fallback that recovers the same information from the existing display timestamp instead.

4.3.2 fixes another way the same report could get stuck at 00:00: clicking Clear Activity Report wiped the one history row a currently-Home person's entry depends on, and since their status hasn't changed, nothing re-logs a replacement, leaving them stuck until their next real departure/arrival. Clearing now re-seeds a fresh Home anchor for everyone currently Home, and the report page also self-heals the same gap on render, covering the case where an anchor row instead ages out of the retention cap naturally over time with no clear involved.

4.3.3 changes how the report renders: it no longer always shows all 30 rows padded out with days nobody could ever have data for (before the instance existed, or before people were configured). It now shows today plus however many days actually have recorded data, dropping the trailing run of untracked days. A genuine 00:00 day sandwiched between days that do have data is still shown, since that's real information rather than padding. The total row's day count reflects whatever's actually visible.

All releases carry forward the B4.0 broader beta package (based on the B3.1.42.3 functional baseline), including mobile control layout corrections and immediate manual-refresh LAN status reporting. None deliberately change the presence decision logic.

## Beta testing notes

- Back up your Hubitat hub before installation and avoid using the app as the sole control for safety, security or life-critical automations.
- Test Home and Away transitions with each enabled evidence source, Guest Mode, manual refresh and the configured departure delay.
- When raising an issue, include the Hubitat platform version, configured evidence types, expected versus actual result, the relevant Activity Report entries, and any diagnostic output.

## Licence and support

This project is provided for beta evaluation. Feedback and defect reports should be raised through the GitHub repository's Issues area once published.
