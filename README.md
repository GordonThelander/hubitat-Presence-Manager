# Presence Manager

**1.0.0 - Hubitat Elevation household occupancy manager**

Presence Manager combines multiple presence signals into one reliable household status for Hubitat. It is designed to avoid a false `Away` result when one phone, integration or network check briefly drops out while other evidence still shows someone is home.

## What it does

| Capability | Behaviour |
|---|---|
| Person-based presence | Tracks up to 10 named people using Hubitat mobile geolocation presence, an optional single local IP address per person, or both. |
| Third-party services | Accepts virtual switches or compatible inputs from Google Home, Alexa, SmartThings and similar integrations as additional household evidence. |
| Conservative departure logic | Treats positive evidence as Home immediately, but applies configurable failure thresholds and departure delays before committing Away. |
| Guest Mode | Keeps the household occupied for a timed period when visitors are present, even when normal person evidence is absent. |
| Unified output | Drives an app-created child device or an existing writable switch / presence device. The child output presents switch, presence, contact and motion states for broad Hubitat compatibility. |
| Operations and diagnostics | Provides an activity report, a per-person Presence Report with CSV export, manual refresh, configurable notifications, force Home/Away controls, evidence diagnostics and mobile-safe dashboards. |

## Decision model

Presence Manager is **fast to Home and conservative to Away**:

- Any credible positive person, service or Guest Mode evidence holds the household state as occupied.
- A phone IP check is useful as local confirmation, not as a sole authoritative proof of absence.
- Configurable arrival and departure delays reduce flapping from brief geolocation, Wi-Fi or integration inconsistencies.
- The app rechecks live evidence before committing an Away state, including when an external output device changes.
- Watchdogs guard against Hubitat's scheduler silently dropping a callback (IP ping checks, Guest Mode expiry and the periodic evidence re-check all use platform-managed recurring jobs rather than a chain that has to re-arm itself).

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

Install through Hubitat Package Manager using this repository's `packageManifest.json`, or use Hubitat's manual code installation flow.

## Usage notes

- Back up your Hubitat hub before installation and avoid using the app as the sole control for safety, security or life-critical automations.
- Test Home and Away transitions with each enabled evidence source, Guest Mode, manual refresh and the configured departure delay.
- When raising an issue, include the Hubitat platform version, configured evidence types, expected versus actual result, the relevant Activity Report entries, and any diagnostic output.

## License

Apache License 2.0 - see [LICENSE](LICENSE).

## Support

Feedback and defect reports should be raised through this repository's Issues area.
