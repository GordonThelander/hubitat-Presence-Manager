# Presence Manager B3.1.42.3 Mobile Controls Fix

## B3.1.42.3 Mobile Controls Fix

- Fixed the main dashboard **Controls** layout on phone-width screens.
- Removed the explicit `width: 1` / `width: 11` Hubitat grid allocation from **Refresh data** and its helper text. That allocation narrowed the button to one grid column and caused the label and helper text to wrap badly on Android.
- **Refresh data**, the helper text and the Force / Guest Mode controls now render as normal full-width mobile rows.
- No presence, ping, manual refresh, scheduling, notification or decision logic changed.

## B3.1.42.2 Manual Refresh LAN Status Fix

- Fixed the **LAN IP** dashboard column after pressing **Refresh data**. It now shows the raw result from the just-completed manual sweep, including the first failed sweep.
- Previously, the person decision path used the manual result correctly, but the LAN IP display still applied the normal two-failure threshold and could incorrectly remain `present` after the first failed manual sweep.
- Scheduled checks and all normal automatic failure-threshold behaviour remain unchanged.

Hubitat app and child drivers for aggregating household presence into a single **application main status** output.

## B3.1.42.1 Manual Refresh Immediate changes

- **Refresh data** now uses a distinct manual execution path built directly from B3.1.41 UI.
- The manual path runs the same complete IP sweep as the normal ping routine, but passes its just-completed result into the actual occupancy evaluation. A first failed manual sweep is therefore treated as unavailable immediately for the manual action.
- The dashboard shows that same one-sweep result for 30 seconds after pressing **Refresh data**. This is timestamp-based rather than a one-use flag, so Hubitat dynamic-page redraws cannot consume the manual result before the table is displayed.
- Scheduled ping checks, normal `IP failure threshold` handling, arrival/departure delays, notifications and drivers are unchanged.

## B3.1.41 UI changes

- Added responsive desktop/mobile rendering for the main dashboard. Desktop retains the telemetry table; phone-width screens now show one stacked card per user/system so Effective Status, Hubitat / Third Party Presence and LAN IP values no longer overlap.
- Added responsive rendering for the Guest Mode timer panel. Phone-width screens now show selected duration and timer status as vertical key/value fields instead of a compressed two-column table.
- Added responsive rendering for the Activity Report. Desktop retains the Time / Event / Detail table; mobile now shows each event as a readable vertical card.
- Added shared Presence Manager CSS with a 640 px breakpoint, mobile-safe word wrapping and full-width mobile action buttons.

## B3.1.40 UI changes

- Added a defensive **Away commit guard** inside the final `setEmpty()` path. Even if a future code path tries to mark the home Away directly, the app rechecks live evidence and blocks the Away commit if any person, Third Party Services input or Guest Mode evidence is still Home.
- Added an **Effective Status** column to the main dashboard. This shows the combined scored person status beside the raw Hubitat mobile presence and LAN IP evidence, so a phone can show `Departed` while the effective user status remains `Home` if the LAN IP is still reachable.
- Activity Report now records blocked Away commits as `Overall away blocked: ...` so false Away attempts are visible instead of silently corrupting the overall state.

## B3.1.39 UI changes

- Fixed external main status/output events being treated as authoritative occupancy changes. External off/not-present events now trigger a normal evidence re-evaluation rather than forcing Overall Away, so one person leaving cannot mark the whole home Away while another person is still present.
- Preserves departure-delay behaviour and user status reporting during output-device changes.

## B3.1.38 UI changes
- Improved Activity Report user status consistency by coalescing individual user departure reports.
- A user's Home report is still recorded immediately, but a Departed report is now held until the absence remains stable long enough for the Hubitat mobile presence child device and the LAN ping result to align.
- This avoids misleading report rows such as `Tanya: Home`, `Tanya: Departed`, `Tanya: Home` caused by short-lived disagreement between geolocation presence and timed IP polling.
- This change affects reporting only. The live evidence model and whole-house committed Home/Away decision logic remain unchanged.

## B3.1.37 UI changes

| Change | Behaviour |
|---|---|
| Refresh helper text moved closer | The **Schedule running in the background, this a manual refresh** note now sits immediately beside the **Refresh data** button instead of being offset across the page. |

## B3.1.36 UI changes

| Change | Behaviour |
|---|---|
| Refresh helper text added | The main dashboard now shows **Schedule running in the background, this a manual refresh** next to **Refresh data**. |
| Expired pending transition self-heal | If a pending Home/Away transition reaches `00:00` and Hubitat has dropped or missed the scheduled job, the status render/evaluator now processes the expired pending transition instead of leaving it stuck. |
| Pending schedule watchdog | Pending arrival/departure transitions now get a secondary watchdog schedule 5 seconds after the due time. |
| Pending reschedule hardening | If app settings are saved during a pending transition, the next evaluation now re-ensures the scheduled handler instead of assuming it still exists. |
| Evidence subscription refresh | Saved user presence devices are resubscribed after save, including a short delayed refresh to handle Hubitat setting persistence timing. |
| Evidence watchdog | A lightweight 60-second evidence watchdog evaluates current presence/switch values as a fallback if a Hubitat device event is missed. |
| Diagnostics expanded | Advanced diagnostics now shows pending schedule status, subscribed presence devices, subscribed switch devices, last evidence event and last subscription refresh. |

## B3.1.35 UI changes

| Change | Behaviour |
|---|---|
| Pending delay countdown shown | The home dashboard overall status now shows the remaining pending delay in **mm:ss** format, for example **Home (Away pending - 09:42 departure delay remaining)**. |
| Pending diagnostics expanded | Advanced diagnostics now also shows the pending delay remaining. |

## B3.1.34 UI changes

| Change | Behaviour |
|---|---|
| Dashboard status aligned to committed state | The home dashboard now shows the committed Home/Away lifecycle state rather than raw live evidence while a delayed transition is still pending. |
| Pending transition labels added | During delay windows, the dashboard shows pending states such as **Home (Away pending)** or **Away (Home pending)**. |
| Committed occupancy diagnostics added | Advanced diagnostics now shows the last committed occupancy state. |

## B3.1.33 UI changes

| Change | Behaviour |
|---|---|
| Overall notification path hardened | Overall status notifications are now transition-driven only. The old last-notified dedupe no longer blocks a real Home/Away transition. |
| Notification diagnostics added | Advanced diagnostics now shows selected notification devices, the last notification attempt, selected device count, accepted device count and failure text. |
| Test notification added | The main dashboard Notification Configuration section now includes **Test Notification** to verify selected Hubitat notification devices independently of presence changes. |
| Warning visibility improved | Notification failures are now written as warnings instead of debug-only messages. |

## B3.1.32 UI changes

| Change | Behaviour |
|---|---|
| Notification regression fixed | Overall status notifications now send when the actual occupancy state changes, even if an old dedupe key matches the new state. |
| Pending configuration no longer poisons notification state | When no users and no Third Party Services are configured, the app holds **Pending user and 3rd party systems configuration**, does not drive the main status to Away, and clears the old overall-notification dedupe key. |
| Notification diagnostics tightened | The app only records the last overall notification after at least one selected notification device accepts the message. |

## B3.1.31 UI changes

| Change | Behaviour |
|---|---|
| Initial overall status now shows pending | When no users and no Third Party Services have been configured yet, the home dashboard **Overall Status** now shows **Pending user and 3rd party systems configuration** instead of **Away**. |

## B3.1.29 UI changes

| Change | Behaviour |
|---|---|
| Initial setup wording changed | The first setup instruction now says **Please choose the name of your main presence switch* before configuring individual users, etc.** |
| Setup helper text removed | Removed the extra explanatory text under **Main status switch assignment** and the duplicate red warning below the save button on the initial home setup view. |
| Main child switch name highlighted | **Application main child switch name** is now bolded so the naming step is harder to miss. |
| Guest Mode child name locked | The Guest Mode child switch name is no longer editable and is always derived from the main switch name with **Guest Mode** appended. |
| Initial setup summary table removed | Removed the switch/current-name summary table from the initial home setup view. |

## B3.1.28 UI changes

| Change | Behaviour |
|---|---|
| Home telemetry wording changed | The home dashboard telemetry column now reads **Hubitat / 3rd Party Presence** instead of **Hubitat Geolocation**. |
| Blank user telemetry cleaned | Empty home dashboard user evidence cells are now blank instead of showing **none configured**. |
| Guest Mode timer editable | The home dashboard now exposes **Guest mode timer (hours)** as a required whole-hour numeric field with a minimum of 1 hour. |
| User wording cleanup | User configuration wording no longer refers to the removed per-user **Test IP** button. |

## B3.1.27 UI changes

| Change | Behaviour |
|---|---|
| Main force buttons restored | **Force Home** and **Force Away** are back on the main Presence Manager dashboard. |
| Advanced force buttons removed | **Force Home** and **Force Away** remain removed from Advanced Configuration. |
| Remove Force retained | **Remove Force** remains on the main dashboard. |

## B3.1.26 UI changes

| Change | Behaviour |
|---|---|
| Advanced force buttons actually removed | Removed the remaining **Force Home** and **Force Away** inputs from the Advanced Configuration diagnostic section. |
| Main dashboard controls retained | The main dashboard still keeps **Force Home**, **Force Away** and **Remove Force**. |

## B3.1.25 UI changes

| Change | Behaviour |
|---|---|
| Advanced force buttons removed | **Force Home** and **Force Away** have been removed from Advanced Configuration. |
| Main dashboard controls retained | **Force Home**, **Force Away** and **Remove Force** remain available on the main Presence Manager dashboard. |

## B3.1.24 UI changes

| Change | Behaviour |
|---|---|
| Initial setup table moved | The Application Child Switch summary table now appears at the bottom of the initial setup screen, below the save/update controls and readiness message. |
| Prior Guest Mode cleanup retained | Guest activity monitoring and the Advanced Guest timer remain removed. Guest Mode capability remains on the main dashboard. |

## B3.1.23 UI changes

| Change | Behaviour |
|---|---|
| Test IP button removed | The per-user **Test IP** button has been removed from the Add/Edit User UI. |
| Automatic IP testing | When a person is saved with an accepted IP address, the app schedules a background IP test after save. |
| Saved IP preferred | The automatic test uses the saved person IP first, with visible draft IP as fallback during creation/edit. |
| Activity report wording | Manual test report wording has been changed to automatic test wording. |

## B3.1.22 UI changes

| Change | Behaviour |
|---|---|
| Clear Activity Report moved | The **Clear Activity Report** button has been moved from Advanced Configuration to the Activity Report page. |
| Advanced diagnostics simplified | Advanced Configuration still shows diagnostics and debug controls, but no longer contains the report-clearing action. |

## B3.1.21 UI changes

| Change | Behaviour |
|---|---|
| Overall status notification path corrected | `setOccupied()` and `setEmpty()` now call the dedicated overall-status notification tracker directly. |
| Device-detection notifications fixed | LAN IP, geolocation and Third Party Services driven state changes now send **Occupied** or **Departed** when the overall state changes. |
| Notification dedupe retained | The app suppresses only repeated notifications for the exact same overall notification state. |
| Diagnostics added | Advanced diagnostics now show the last notification text and timestamp. |

## B3.1.20 UI changes

| Change | Behaviour |
|---|---|
| Overall-status notifications made authoritative | Notifications now fire from a dedicated overall-status transition tracker, not only from the internal `currentOccupancy` change flag. |
| Device-detection changes now notify | When geolocation, LAN IP or Third Party Services changes drive the overall state to Occupied or Departed, the app sends the matching notification. |
| Forced/unforced notification states deduped separately | `Occupied`, `Occupied (Forced)`, `Departed` and `Departed (Forced)` are tracked as separate notification states, so forced-state changes are not silently swallowed. |
| Duplicate suppression retained | Repeated evaluations of the same overall state do not spam notifications. |

## B3.1.19 UI changes

| Change | Behaviour |
|---|---|
| Notification toggles removed | The three notification enable/disable toggles have been removed from the main dashboard. Notifications are now sent whenever notification devices are configured. |
| Guest activity monitoring removed | Guest activity motion/contact sensor configuration, subscriptions and handler logic have been removed. Guest Mode is now controlled only by the main Guest Mode controls/child switch. |
| Advanced Guest timer removed | The Guest Mode duration input has been removed from Advanced Configuration. The main dashboard Guest Mode controls and 4-hour timer behaviour remain in place. |
| Diagnostics cleaned | Removed the Last Guest Mode activity diagnostic row because guest activity monitoring no longer exists. |

## B3.1.18 UI changes

| Change | Behaviour |
|---|---|
| Main status device notifications added | Presence Manager now subscribes to the configured main/master status device `switch` and `presence` attributes, so manual changes made directly on the child/master device can generate notifications. |
| App-driven duplicate suppression | When the app itself drives the main status device, the resulting device event is suppressed to avoid duplicate notifications. |
| Force button notification reliability | Force Home and Force Away now send the forced notification even when the state was already occupied/departed. |
| Guest Mode child switch notifications fixed | Manual Guest Mode child switch on/off now sends the approved Guest Mode notification wording and sets/clears the timer correctly. |

## B3.1.17 UI changes

| Change | Behaviour |
|---|---|
| Notification text standardised | Notifications now use only the requested state wording: **Occupied**, **Occupied (Forced)**, **Departed**, **Departed (Forced)**, **Guest Mode on until [planned time]**, or **Guest Mode Off**. |
| Force-aware state notifications | Force Home and Force Away now send forced-specific wording when they change the occupancy state. |
| Guest Mode notification wording | Guest Mode start/activity notifications report the planned end time. Guest Mode cancel/expiry reports **Guest Mode Off**. |

## B3.1.16 UI changes

| Change | Behaviour |
|---|---|
| Page width restored | Removed the broad `.mdl-card` / navigation-card CSS that compressed the Hubitat page into a narrow left column. |
| Button width retained safely | Kept only the actual action-button width normalisation, without touching Hubitat page/card layout classes. |
| Notification selector tidied | Shortened the notification selector helper text so Hubitat's blue selector text does not spill across the tile. |

## B3.1.15 UI changes

| Change | Behaviour |
|---|---|
| Dashboard selector/link tiles widened | The Notification devices tile on the main dashboard is now wider so the blue supporting text stays inside the tile. |
| Navigation tiles matched | Manage Users, Activity Report and Advanced Configuration now use the same widened tile width for a cleaner aligned layout. |
| Text wrapping improved | Tile title/supporting text is allowed to wrap cleanly inside the wider cards. |

## B3.1.14 UI changes

| Change | Behaviour |
|---|---|
| Notification device selector restored to main dashboard | The actual `capability.notification` selector is now back on the main screen, so notification devices can be added directly there. |
| Removed broken navigation pattern | Removed the Configure notification devices link because it bounced to Advanced Configuration instead of configuring notifications directly. |
| Advanced Configuration cleaned | Notification device selection is no longer duplicated in Advanced Configuration. |

## B3.1.13 UI changes

| Change | Behaviour |
|---|---|
| Configure notification devices link added | The main dashboard Notification Configuration section now includes a direct **Configure notification devices** link that opens Advanced Configuration where notification devices are selected. |

## B3.1.12 UI changes

| Change | Behaviour |
|---|---|
| Blue device-link text removed from dashboard | The main dashboard no longer renders the live `notificationDevices` selector, which Hubitat displays as blue linked text. |
| Cleaner notification section on dashboard | The main page now shows a plain-text summary of configured notification devices plus the three notification toggles. |
| Device selection moved to Advanced Configuration | Notification device selection remains available, but it now lives in Advanced Configuration to keep the dashboard cleaner. |

## B3.1.11 UI changes

| Change | Behaviour |
|---|---|
| Notification section fixed | Notification Configuration now appears on the main dashboard immediately above Navigation. |
| Top gap removed | Removed the empty CSS-only section that was causing the large blank gap below the page title. |
| Advanced Configuration cleaned | Notification Configuration remains on the main dashboard only, not duplicated in Advanced Configuration. |

## B3.1.10 UI changes

| Change | Behaviour |
|---|---|
| Notification controls moved to dashboard | Notification device selection plus the three notification toggles now appear on the main screen just above Navigation. |
| Advanced Configuration simplified | Notification Configuration has been removed from Advanced Configuration to avoid duplication. |

## B3.1.9 UI changes

| Change | Behaviour |
|---|---|
| Forced label colour aligned | **(FORCED)** now renders in the same colour as the active Overall Status: green for Home, red for Away. |
| Remove Force button added | New **Remove Force** control clears the sticky Force Home / Force Away override and immediately returns the app to normal evidence-based evaluation. |

## B3.1.8 UI changes

| Change | Behaviour |
|---|---|
| Forced status label | Overall Status now displays **Home (FORCED)** or **Away (FORCED)** when Force Home or Force Away is active. |

## B3.1.7 UI changes

| Change | Behaviour |
|---|---|
| Force Home / Force Away made sticky | Force Home and Force Away now act as manual occupancy overrides. They immediately set the main occupancy state and keep it there until the override is changed by pressing the opposite force button. |
| Override wins over evidence | Geolocation, LAN IP, Third Party Services and Guest Mode evaluations no longer automatically reverse a forced state while the override is active. |
| Override visibility | Overall Status now shows when the displayed state is being held by a Force Override. |

## B3.1.6 UI changes

| Change | Behaviour |
|---|---|
| Dashboard control buttons normalised | Refresh data, Force Home, Force Away, Guest Mode On and Guest Mode Off now render at a consistent fixed width on the main dashboard. |

## B3.1.2 UI changes

| Change | Behaviour |
|---|---|
| Notification model cleaned up | Email-specific configuration has been removed. Presence Manager now uses Hubitat **Notification devices** only. |
| Email removed | No email address field, no `sendMail()` call and no email-specific wording. |
| Pending-empty notification removed | The old **Notify when empty is pending** option has been removed to avoid noisy or confusing pre-state alerts. |
| State-change notifications retained | Optional notification when application main status changes to occupied or empty remains available. |
| Guest Mode notifications retained | Optional notification when Guest Mode starts, cancels or expires remains available. |
| Guest activity notifications retained | Optional notification when activity is detected while Guest Mode is active remains available. |
| B2.19 UX baseline retained | Main dashboard, User Configuration page, Configure Presence Manager page, Guest Mode timer and stage-gated setup are retained. |
| Activity Report cleaned up | The report now only shows individual user status changes, Guest Mode started/stopped events and overall Home/Away status changes. Configuration, initialisation, ping tests, pending-empty decisions and guest activity sensor noise are no longer shown. |

## Current UI model

| Page | Purpose |
|---|---|
| Main page | Operational dashboard showing Evaluation Status Summary, Guest Mode controls/timer, evidence roll-up and saved users summary. |
| User Configuration | Add, save, edit, delete and test users. |
| Advanced Configuration | Application Child Switch Configuration, Third Party Services, Guest Mode settings and Advanced controls. |

## Main page after setup

| Section | Purpose |
|---|---|
| Evaluation Status Summary | Shows the current application main status, Guest Mode state and evidence roll-up. |
| Guest Mode | Starts/cancels Guest Mode and shows configured duration, active end time and remaining time. |
| Users | Shows saved users as a summary table. User edits are handled in User Configuration. |
| Configure Presence Manager | Opens configuration areas for application switch, Third Party Services, Guest Mode settings and Advanced tuning. |

## application main status output

The application main status remains the first setup step. Until a application main status output is created or nominated, other configuration is blocked.

The app can either:

1. Create/use the managed child device called `application main status`,
2. Drive an existing nominated switch, or
3. Drive an existing writable presence/aggregator device.

The child driver exposes:

| Capability | Occupied | Empty |
|---|---|---|
| Switch | on | off |
| PresenceSensor | present | not present |
| ContactSensor | open | closed |
| MotionSensor | active | inactive |

## People workflow

| Step | UI behaviour |
|---|---|
| Fresh install | Configure the application main status first. |
| Add Person 1 | Open User Configuration, enter a known name, optional phone IP and Hubitat mobile app geolocation presence sensor, then press **Save Person**. |
| Add another user | After a saved user exists, press **Add another user** only if another user is required. |
| Edit | Saved users can be edited from User Configuration. |
| Delete | Saved users from Person 2 onward can be deleted. Person 1 remains the base user. |
| Limit | Maximum 10 users. |

## Inputs

| Input type | Purpose |
|---|---|
| Hubitat mobile app geolocation presence sensor | Primary per-person presence signal. `present` means the user is within the Hubitat mobile app geofence. |
| Phone IP address | Optional secondary local LAN evidence. One reserved IP per user only, to avoid secondary devices left at home causing false presence. |
| Third Party Services | Optional whole-house signal, such as Google Home, Alexa or SmartThings presence mirrored into Hubitat. |
| Guest Mode switch | Automatic child switch used to hold the household occupied while guests are present. |
| Guest activity sensors | Motion/contact devices that record activity while Guest Mode is active. |
| Notification devices | Optional Hubitat notification-capable devices used for alerts. No native email configuration is included. |

## Notifications

| Option | Behaviour |
|---|---|
| Notification devices | Select one or more Hubitat devices that support `deviceNotification`. |
| Notify when occupied/empty changes | Sends a notification only after the application main status changes state. |
| Notify when Guest Mode activity is detected | Sends a notification when motion/contact activity is detected while Guest Mode is active. |
| Notify when Guest Mode starts/cancels/expires | Sends a notification for Guest Mode lifecycle events. |

## Guest Mode

| Behaviour | Detail |
|---|---|
| Child switch naming | Automatically derived from the application main status target, with `Guest Mode` appended. |
| Active state | Holds application main status occupied. |
| Main-screen timer | Shows selected duration, calculated end time and active remaining time. |
| Start / cancel | Start and cancel buttons remain on the main operational dashboard. |
| Activity | Motion/contact activity is recorded only while Guest Mode is active. |

## Recommended defaults

| Setting | Recommended value |
|---|---:|
| Ping interval | 300 seconds / 5 minutes |
| Ping count | 2 |
| IP failure threshold | 2-5 |
| Arrival delay | 0 seconds |
| Departure delay | 5-15 minutes |
| Person present threshold | 60 |
| Third Party Services threshold | 60 |
| Guest Mode duration | 4 hours |

## Install order

1. Install `drivers/Presence_Manager_Output.groovy`.
2. Install `drivers/Presence_Manager_Guest_Mode_Switch.groovy`.
3. Install `apps/Presence_Manager.groovy`.
4. Open the app and configure **application main status** first.
5. Open **User Configuration** and add users.
6. Configure **Third Party Services** where required.
7. Configure **Guest Mode**.
8. Use **Advanced** only when tuning scoring, ping, notifications or diagnostics.

## Notes

A read-only `PresenceSensor` cannot be driven by another app. If using an existing presence / aggregator device as the application main status, it must expose a writable command such as `on/off`, `markPresent/markAway`, `arrived/departed` or equivalent.


## Activity Report scope

| Included in Activity Report | Excluded from Activity Report |
|---|---|
| Individual user status changes: Home / Departed | Routine scheduled evaluations |
| Guest Mode started / stopped | IP ping test noise |
| Overall Home / Away status changes | Configuration saves, initialisation, pending-empty and hold decisions |
|  | Guest activity sensor events, unless used indirectly to change overall status |
