# Presence Manager

**4.6.4 Beta - Hubitat Elevation household occupancy manager**

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

## 4.6.4 beta scope

Carries forward the B4.0 broader beta package (based on the B3.1.42.3 functional baseline). Highlights since 4.0: a Presence Report page showing each user's hours present per calendar day over a rolling 90 day window (30 days prior to 4.6.2); an optional Location Lookup line on the main page (reverse-geocodes the hub's configured coordinates via OpenStreetMap, off by default, never looked up automatically); every table in the app auto-widths its columns to content instead of fixed percentages; and watchdogs for two scheduling failure modes (stuck IP ping checks, stuck Guest Mode expiry) that could otherwise silently persist until a manual refresh, plus explicit "stale, not counted" flagging on the dashboard when that happens. 4.6.1 fixed the default "ignore non-IP evidence after N hours" threshold (12 to 48 hours). 4.6.2 widened the Presence Report to 90 days and added a CSV export link under the Presence Report table. 4.6.3 fixed the Decision detail table still wrapping text letter by letter on mobile (root cause was `overflow-wrap:anywhere` letting table auto-layout collapse a column's minimum width, now `overflow-wrap:break-word` everywhere). 4.6.4 fixed the CSV export twice over: 4.6.3 had switched it to decimal hours to dodge spreadsheet apps auto-reformatting `hh:mm` values, but the export now keeps `hh:mm` text exactly as shown on the page, forced literal via a spreadsheet-formula cell trick so Excel/Sheets/Numbers stop auto-typing it as a time or date. See the git history for the detailed per-version changelog. None of this deliberately changes the presence decision logic.

## Beta testing notes

- Back up your Hubitat hub before installation and avoid using the app as the sole control for safety, security or life-critical automations.
- Test Home and Away transitions with each enabled evidence source, Guest Mode, manual refresh and the configured departure delay.
- When raising an issue, include the Hubitat platform version, configured evidence types, expected versus actual result, the relevant Activity Report entries, and any diagnostic output.

## Licence and support

This project is provided for beta evaluation. Feedback and defect reports should be raised through the GitHub repository's Issues area once published.
