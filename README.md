# Presence Manager

**Household occupancy manager for Hubitat Elevation**

Presence Manager combines multiple presence signals - phone-based mobile presence, a local network check, and (optionally) third-party services like Google Home or SmartThings - into one reliable Home/Away status per person and for the household as a whole. It is built to avoid the common false-Away problem where one flaky signal (a phone briefly dropping off Wi-Fi, a geofence blip) triggers automations as if everyone had left, even though other evidence clearly shows someone is still home.

## Why you might want this

If you already have one presence sensor per person and it "just works," you may not need this. Presence Manager is for households where:

- Different people are tracked with different combinations of signals (some with phone presence, some with a local IP, some with both), and you want one combined household status regardless of who's providing evidence at any given moment.
- A single dropped signal has previously caused a false "everyone's away" automation to fire.
- You want visitors accounted for without treating them as a full household member (Guest Mode).
- You want to actually see *why* the app decided what it decided, not just a black-box Home/Away switch - Presence Manager's diagnostics show the exact evidence and scoring behind every decision.

## What it does

| Capability | Behaviour |
|---|---|
| Person-based presence | Tracks up to 10 named people using Hubitat mobile geolocation presence, an optional local IP address per person, or both. |
| Third-party services | Accepts virtual switches or compatible inputs from Google Home, Alexa, SmartThings and similar integrations as supplementary household evidence. Reliability of these varies by platform - see note below. |
| Conservative departure logic | Treats positive evidence as Home immediately, but applies configurable failure thresholds and departure delays before committing Away. |
| Guest Mode | Keeps the household occupied for a timed period when visitors are present, even when normal person evidence is absent. |
| Unified output | Drives an app-created child device or an existing writable switch / presence device, so it plugs into Rule Machine, dashboards, or anything else that reads a switch or presence sensor. |
| Reporting and diagnostics | An Activity Report of every decision made, a per-person Presence Report (90 day window, CSV export) showing hours home per day, manual refresh/force controls, and a Diagnostic Mode page showing the exact evidence, score and reasoning behind the current decision. |

A note on third-party services: platforms like Google Home have their own presence-detection systems that sit outside Hubitat entirely, and their reliability varies - some fire consistently, others rarely. Person-based mobile presence and IP checks are Presence Manager's core, dependable evidence; third-party switches are best treated as a nice-to-have supplement, not something to depend on alone.

## Decision model

Presence Manager is **fast to Home and conservative to Away**:

- Any credible positive person, service or Guest Mode evidence holds the household state as occupied.
- A phone IP check is useful as local confirmation, not as a sole authoritative proof of absence.
- Configurable arrival and departure delays reduce flapping from brief geolocation, Wi-Fi or integration inconsistencies.
- The app rechecks live evidence before committing an Away state, including when an external output device changes.
- Scheduled checks (IP pings, Guest Mode expiry, periodic evidence review) use platform-managed recurring jobs rather than a chain that has to re-schedule itself each time - so a single missed callback can't quietly stop the whole thing from re-checking.

## What you'll need

- A Hubitat Elevation hub, platform version 2.3.0 or later.
- For phone-based presence: the Hubitat mobile app installed and configured on each person's phone, with a Hubitat mobile presence device already created for them (standard Hubitat mobile app setup, not something this package creates for you).
- Everything else - the local IP check, Guest Mode, third-party inputs - is optional.

## Installation

Through Hubitat Package Manager (recommended): search for **Presence Manager** and install. HPM installs both drivers and the app together.

Manually: in **Drivers Code**, create new drivers from `drivers/Presence_Manager_Output.groovy` and `drivers/Presence_Manager_Guest_Mode_Switch.groovy`. In **Apps Code**, create a new app from `apps/Presence_Manager.groovy`. Then add the app as a user app from the **Apps** page.

## First-time setup

1. Open the app. The first thing it asks for is a **main status target** - this is the single switch or presence device that represents "is the household occupied," which everything else (Rule Machine, dashboards) reads from. Easiest option: let the app create and manage its own child switch for you (the default). Alternatively, point it at a switch or presence device you already have.
2. Once that's set, add **people**: give each a name, and attach a Hubitat mobile presence sensor, a local IP address, or both. More evidence per person means fewer false Away results for them specifically.
3. Optionally configure **Third Party Services** (Google Home, Alexa, SmartThings switches), **Guest Mode**, **notifications**, and the evidence weighting/delay settings under Advanced Configuration - all have sensible defaults and can be left alone to start.
4. Use the dashboard's **Refresh data** and **Evaluate now** controls to confirm the household status looks right before relying on it in automations. **Test Notification** confirms your notification devices are wired up correctly.

## Package contents

| File | Purpose |
|---|---|
| `apps/Presence_Manager.groovy` | The Presence Manager application, configuration screens, evaluation engine, dashboard, reporting and notifications. |
| `drivers/Presence_Manager_Output.groovy` | Main output child driver. Exposes switch, presence, contact and motion capabilities. |
| `drivers/Presence_Manager_Guest_Mode_Switch.groovy` | Managed child switch used for Guest Mode state. |
| `packageManifest.json` | Hubitat Package Manager manifest for GitHub-hosted installation. |

## Usage notes

- Back up your Hubitat hub before installation and avoid using the app as the sole control for safety, security or life-critical automations.
- Test Home and Away transitions with each enabled evidence source, Guest Mode, manual refresh and the configured departure delay before relying on it.
- When raising an issue, include the Hubitat platform version, configured evidence types, expected versus actual result, the relevant Activity Report entries, and any diagnostic output from Advanced Configuration → Diagnostic Mode.

## License

Apache License 2.0 - see [LICENSE](LICENSE).

## Support

Feedback and defect reports should be raised through this repository's Issues area.
