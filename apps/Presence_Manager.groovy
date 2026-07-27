/*
 * Presence Manager
 * Namespace: Hubitat Integrations
 * Version: 4.3
 * Release: Adds the Presence Report page on top of 4.2. Based on the B4.0 broader beta baseline (B3.1.42.3 functional baseline).
 * 4.1 fixed: Activity Report per-user status could be misattributed to the
 * wrong person after deletePerson() shifted a later person into a deleted
 * slot, because atomicState.personStatusSnapshot stayed keyed to the old
 * index. Also hardened addHistory() so two people sharing a display name
 * cannot suppress each other's Home/Departed report rows.
 * 4.2 fixes: configPage() only showed the "main status switch is ready"
 * confirmation when outputMode was "child", hiding it for the "existing" and
 * "presence" assignment modes, because the switch-assignment block had been
 * copy-pasted across mainPage/configPage/advancedConfigPage and drifted.
 * That block is now a single shared method used by all three pages. Also:
 * the "Last 500 report events" labels now reflect the configurable retention
 * setting instead of a hardcoded 500, removed the dead historyHtml() method,
 * documented the unreachable "mixed evidence" fallback in evaluateOccupancy(),
 * and the per-person Save button now shows its own index.
 * 4.3 adds: a new Presence Report page (linked from the main page, below
 * Activity Report) showing each user's hours present per calendar day over
 * a rolling 30 day window, plus a 30-day total row. Built on top of the
 * existing Activity Report "user status" entries; addHistory() now also
 * stores a timeMs epoch value on every row so durations don't need to be
 * parsed back out of the display timestamp string.
 *
 * Purpose:
 * - Aggregate household presence from named people, Hubitat mobile geolocation presence devices, phone IP checks, Third Party Services switches and Guest Mode.
 * - Use Guest Mode as the single combined guest / visitor override model.
 * - Drive an application main status switch at the top of the app configuration.
 *
 * Decision principle:
 * - Fast to occupied, conservative to empty.
 * - Guest Mode overrides normal absence and holds the house occupied.
 * - Presence wins conflicts.
 */

definition(
    name: "Presence Manager",
    namespace: "Hubitat Integrations",
    author: "Gordon Thelander",
    description: "Household presence manager using Hubitat mobile geolocation, optional IP test checks, Third Party Services switches and Guest Mode.",
    category: "Convenience",
    iconUrl: "https://raw.githubusercontent.com/hubitat/HubitatPublic/master/resources/icons/blank.png",
    iconX2Url: "https://raw.githubusercontent.com/hubitat/HubitatPublic/master/resources/icons/blank.png",
    singleInstance: true,
    installOnOpen: true
)

preferences {
    page(name: "mainPage", title: "", install: true, uninstall: true)
    page(name: "configPage", title: "Application Child Switch Configuration", install: false, uninstall: false)
    page(name: "peoplePage", title: "User Configuration", install: false, uninstall: false)
    page(name: "activityReportPage", title: "Activity Report", install: false, uninstall: false)
    page(name: "presenceReportPage", title: "Presence Report", install: false, uninstall: false)
    page(name: "advancedConfigPage", title: "Advanced Configuration", install: false, uninstall: false)
}

def installed() {
    initialiseState()
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialiseState()
    initialize()
}

def uninstalled() {
    unsubscribe()
    unschedule()
}

def appButtonHandler(String btn) {
    if (btn == "createChildBtn") {
        createOrUpdateOutputChild()
        if (masterPresenceStatusConfigured()) {
            atomicState.masterSetupMessage = "Application switch configuration has been updated."
            addHistory("configuration", "Application switch configuration created / updated")
            schedulePostMasterSetup()
        }
        return
    }

    if (!masterPresenceStatusConfigured()) {
        atomicState.lastDecision = "Blocked - configure the application main status switch first"
        addHistory("blocked", atomicState.lastDecision)
        logWarn(atomicState.lastDecision)
        return
    }

    if (btn == "createGuestModeSwitchBtn") {
        schedulePostMasterSetup()
    }

    if (btn?.startsWith("savePersonBtn")) {
        Integer saveIndex = personSaveIndexFromButton(btn)
        if (saveIndex) {
            savePerson(saveIndex)
            unsubscribe()
            unschedule()
            initialize(true)
            schedulePersonIpTest(saveIndex)
            runIn(2, "refreshEvidenceSubscriptions", [overwrite: true])
        }
    }

    if (btn == "createPersonBtn") {
        Integer saveIndex = currentDraftPersonIndex()
        if (saveIndex) {
            savePerson(saveIndex)
            unsubscribe()
            unschedule()
            initialize(true)
            schedulePersonIpTest(saveIndex)
            runIn(2, "refreshEvidenceSubscriptions", [overwrite: true])
        }
    }

    if (btn == "addPersonBtn") {
        addAnotherPerson()
        unsubscribe()
        unschedule()
        initialize(true)
    }

    if (btn?.startsWith("editPersonBtn")) {
        Integer editIndex = personEditIndexFromButton(btn)
        if (editIndex) {
            editPerson(editIndex)
        }
    }

    if (btn?.startsWith("cancelEditPersonBtn")) {
        Integer cancelIndex = personCancelEditIndexFromButton(btn)
        if (cancelIndex) {
            cancelEditPerson(cancelIndex)
        }
    }
    if (btn == "pingNowBtn") runPingChecks()

    if (btn == "refreshDataBtn") {
        // Manual Refresh data intentionally uses one completed IP sweep as the
        // immediate decision input. Scheduled checks continue to use the normal
        // IP failure threshold and are not changed by this path.
        runManualDashboardRefresh()
    }

    if (btn == "evaluateNowBtn") {
        if (allConfiguredIps()) runPingChecks()
        else evaluateOccupancy("manual evaluation")
    }

    if (btn?.startsWith("deletePersonBtn")) {
        Integer deleteIndex = personIndexFromButton(btn)
        if (deleteIndex) {
            deletePerson(deleteIndex)
            unsubscribe()
            unschedule()
            initialize(true)
        }
    }

    if (btn == "startGuestModeBtn") startGuestMode()
    if (btn == "cancelGuestModeBtn") cancelGuestMode()
    if (btn == "clearHistoryBtn") clearHistory()
    if (btn == "testNotificationBtn") sendTestNotification()
    if (btn == "forceHomeBtn") forceOverrideHome()
    if (btn == "forceAwayBtn") forceOverrideAway()
    if (btn == "removeForceBtn") removeForceOverride()
}

def mainPage(params = null) {
    initialiseState()
    return dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        if (!masterPresenceStatusConfigured()) {
            section("<b>Application Child Switch Configuration</b>") {
                paragraph "<span style='color:red;'>Please choose the name of your main presence switch* before configuring individual users, etc.</span>"
                outputSwitchAssignmentInputs()
                if (masterPresenceStatusConfigured()) outputSwitchReadyStatusHtml(false)
            }
        } else {
            section("") {
                paragraph statusHtml()
            }

            section("<b>Controls</b>") {
                // Keep the refresh control and its helper text as full-width rows.
                // Explicit 1/11 Hubitat grid widths collapse the Refresh data button
                // and wrap the helper text on phone-width screens.
                input "refreshDataBtn", "button", title: "Refresh data", submitOnChange: true
                paragraph "<span style='color:#777;'>Schedule running in the background, this a manual refresh</span>"
                input "forceHomeBtn", "button", title: "Force Home", submitOnChange: true
                input "forceAwayBtn", "button", title: "Force Away", submitOnChange: true
                input "removeForceBtn", "button", title: "Remove Force", submitOnChange: true

                input "guestModeDurationHours", "number",
                    title: "Guest mode timer (hours)",
                    description: "Whole hours only. Minimum 1.",
                    defaultValue: 4,
                    required: true,
                    range: "1..168",
                    submitOnChange: true

                if (guestModeDurationHoursIsValid()) {
                    input "startGuestModeBtn", "button", title: "Guest Mode On", submitOnChange: true
                } else {
                    paragraph "<span style='color:red;font-weight:bold;'>Guest Mode requires a whole number of hours, minimum 1.</span>"
                }
                input "cancelGuestModeBtn", "button", title: "Guest Mode Off", submitOnChange: true

                paragraph guestModeMainTimerHtml()
            }

            section("<b>Notification Configuration</b>") {
                input "notificationDevices", "capability.notification",
                    title: "Notification devices",
                    description: "Select devices.",
                    multiple: true,
                    required: false,
                    submitOnChange: true
                input "testNotificationBtn", "button", title: "Test Notification", submitOnChange: true
            }

            section("<b>Navigation</b>") {
                href(name: "peopleConfigLink", title: "Manage Users", description: "Add, edit or delete users.", page: "peoplePage")
                href(name: "activityReportLink", title: "Activity Report", description: "Last ${historyLimitValue()} report events.", page: "activityReportPage")
                href(name: "presenceReportLink", title: "Presence Report", description: "Daily hours home, last ${presenceReportDayCount()} days.", page: "presenceReportPage")
                href(name: "advancedConfigLink", title: "Advanced Configuration", description: "Third Party Services, weighting, IP and diagnostics.", page: "advancedConfigPage")
            }
        }
    }
}

// Shared main-status-switch assignment inputs used by mainPage's initial setup gate,
// configPage and advancedConfigPage, so the three pages cannot drift out of sync again.
void outputSwitchAssignmentInputs() {
    input "outputMode", "enum",
        title: "Main status switch assignment",
        options: [
            "child":"Create / use the application main child switch",
            "existing":"Assign main status to an existing switch",
            "presence":"Assign main status to an existing writable presence / aggregator device"
        ],
        defaultValue: "child",
        required: true,
        submitOnChange: true

    String selectedOutputMode = (outputMode ?: "child").toString()
    if (selectedOutputMode == "existing") {
        input "outputSwitch", "capability.switch",
            title: "Existing switch to use as the main status target",
            description: "on = Home, off = Away. Use this when you want Presence Manager to drive an existing virtual switch.",
            multiple: false,
            required: true,
            submitOnChange: true
    } else if (selectedOutputMode == "presence") {
        input "outputPresenceDevice", "capability.presenceSensor",
            title: "Existing writable presence / aggregation device to use as the main status target",
            description: "The target must expose writable commands such as on/off, markPresent/markAway, arrived/departed or present/notPresent. A read-only presence sensor cannot be driven.",
            multiple: false,
            required: true,
            submitOnChange: true
    } else {
        input "childDeviceName", "text",
            title: "<b>Application main child switch name</b>",
            defaultValue: "Presence Manager Main Status",
            required: true,
            submitOnChange: true
    }

    paragraph "<b>Application Guest Mode child switch name</b><br/>${html(configuredGuestModeChildName())}"
    input "createChildBtn", "button", title: "Save / update application switch configuration", submitOnChange: true
}

// Shared "ready" confirmation shown once the main status switch is configured.
// masterPresenceStatusConfigured() is true for all three assignment modes (child,
// existing, presence), so this must not be gated on outputMode == "child" the way
// configPage used to be - that hid the ready message for the other two modes.
void outputSwitchReadyStatusHtml(Boolean includeGuestSwitchStatus) {
    paragraph "<span style='color:green;font-weight:bold;'>Application main status switch is ready.</span>"
    if (includeGuestSwitchStatus) paragraph guestSwitchCreatedHtml()
}

String uniformButtonWidthCssHtml() {
    return responsiveUiCssHtml()
}

String responsiveUiCssHtml() {
    return """
<style>
/* Presence Manager responsive UI - keeps desktop tables and switches phones to stacked cards. */
.pm-desktop { display:block; }
.pm-mobile { display:none; }
.pm-table-wrap { width:100%; max-width:100%; overflow-x:auto; }
.pm-table { border-collapse:collapse; width:100%; table-layout:fixed; }
.pm-table th, .pm-table td { border:1px solid #ddd; padding:4px; text-align:left; vertical-align:top; overflow-wrap:anywhere; word-break:break-word; white-space:normal; }
.pm-overall { display:flex; gap:24px; align-items:center; padding:4px 0; flex-wrap:wrap; }
.pm-overall-label { font-weight:bold; min-width:150px; }
.pm-card { border:1px solid #ddd; border-radius:8px; padding:10px; margin:10px 0; background:#fff; box-sizing:border-box; max-width:100%; overflow:hidden; }
.pm-card-title { font-weight:bold; font-size:16px; margin-bottom:8px; }
.pm-field { margin-top:8px; }
.pm-label { font-weight:bold; color:#444; font-size:12px; line-height:1.25; }
.pm-value { margin-top:2px; line-height:1.35; overflow-wrap:anywhere; word-break:break-word; white-space:normal; }
.pm-muted { color:#777; }
.pm-event-card { border:1px solid #ddd; border-radius:8px; padding:10px; margin:10px 0; background:#fff; box-sizing:border-box; }
.pm-event-time { font-weight:bold; margin-bottom:6px; }
.pm-event-type { color:#444; font-weight:bold; margin-bottom:4px; }

/* Presence report table - deliberately does not use width:100%/table-layout:fixed like
   .pm-table, so it can grow wider than its wrapper as people are added and actually
   trigger the .pm-table-wrap horizontal scrollbar on narrow screens instead of just
   squeezing columns unreadably thin. */
.pm-scroll-table { border-collapse:collapse; }
.pm-scroll-table th, .pm-scroll-table td { border:1px solid #ddd; padding:4px 10px; text-align:left; white-space:nowrap; }
.pm-scroll-table th.pm-date-col, .pm-scroll-table td.pm-date-col { min-width:150px; }
.pm-scroll-table th.pm-hours-col, .pm-scroll-table td.pm-hours-col { min-width:90px; text-align:right; }
.pm-scroll-table tr.pm-total-row td { border-top:2px solid #444; font-weight:bold; }

/* Keep only real action buttons normalised. Do not touch Hubitat card/page layout classes. */
input[type='submit'],
button,
.btn {
    min-width:132px !important;
    width:132px !important;
    box-sizing:border-box !important;
    text-align:center !important;
}

@media only screen and (max-width: 640px) {
    .pm-desktop { display:none !important; }
    .pm-mobile { display:block !important; }
    .pm-overall { display:block; }
    .pm-overall-label { min-width:0; margin-bottom:6px; }
    input[type='submit'], button, .btn {
        min-width:0 !important;
        max-width:100% !important;
        width:100% !important;
        margin-top:6px !important;
    }
}
</style>
"""
}

def configPage(params = null) {
    initialiseState()
    return dynamicPage(name: "configPage", title: "Application Child Switch Configuration", install: false, uninstall: false) {
        section("<b>Navigation</b>") {
            href(name: "mainPageLink", title: "Back to status dashboard", description: "Return to the operational dashboard.", page: "mainPage")
            href(name: "advancedPageLink", title: "Advanced Configuration", description: "Open the full configuration screen.", page: "advancedConfigPage")
        }

        section("<b>Application Child Switch Configuration</b>") {
            paragraph "This page controls the two app-level switches: the main household status target and the Guest Mode child switch."
            paragraph applicationChildSwitchSummaryHtml()

            outputSwitchAssignmentInputs()

            if (masterPresenceStatusConfigured()) {
                outputSwitchReadyStatusHtml(true)
            } else {
                paragraph "<span style='color:red;font-weight:bold;'>Configure the application main status switch before configuring users, services, Guest Mode or advanced behaviour.</span>"
            }
        }
    }
}

def activityReportPage(params = null) {
    initialiseState()
    return dynamicPage(name: "activityReportPage", title: "Activity Report", install: false, uninstall: false) {
        section("<b>Navigation</b>") {
            href(name: "mainPageLinkFromActivity", title: "Back to status dashboard", description: "Return to the operational dashboard.", page: "mainPage")
        }

        section("<b>Activity Report Controls</b>") {
            input "clearHistoryBtn", "button", title: "Clear Activity Report", submitOnChange: true
        }

        section("<b>Last ${historyLimitValue()} report events</b>") {
            paragraph activityReportHtml()
        }
    }
}

def presenceReportPage(params = null) {
    initialiseState()
    return dynamicPage(name: "presenceReportPage", title: "Presence Report", install: false, uninstall: false) {
        section("<b>Navigation</b>") {
            href(name: "mainPageLinkFromPresenceReport", title: "Back to status dashboard", description: "Return to the operational dashboard.", page: "mainPage")
        }

        section("<b>Rolling ${presenceReportDayCount()} day presence report</b>") {
            paragraph "Hours each user was present, by calendar day. Today's row is a running total, not a finished day."
            paragraph presenceReportHtml()
        }
    }
}

def advancedConfigPage(params = null) {
    initialiseState()
    return dynamicPage(name: "advancedConfigPage", title: "Advanced Configuration", install: false, uninstall: false) {
        section("<b>Navigation</b>") {
            href(name: "mainPageLinkFromAdvanced", title: "Back to status dashboard", description: "Return to the operational dashboard.", page: "mainPage")
            href(name: "peoplePageLinkFromAdvanced", title: "Manage Users", description: "Open User Configuration.", page: "peoplePage")
            href(name: "activityPageLinkFromAdvanced", title: "Activity Report", description: "Last ${historyLimitValue()} report events.", page: "activityReportPage")
        }

        section("<b>Application Child Switch Configuration</b>") {
            paragraph "This page controls the two app-level switches: the main household status target and the Guest Mode child switch. Keep it available here for recovery if either switch is renamed, replaced or deleted."
            paragraph applicationChildSwitchSummaryHtml()

            outputSwitchAssignmentInputs()

            if (masterPresenceStatusConfigured()) {
                outputSwitchReadyStatusHtml(true)
                input "evaluateNowBtn", "button", title: "Evaluate now", submitOnChange: true
            } else {
                paragraph "<span style='color:red;font-weight:bold;'>Configure the application main status switch before configuring advanced behaviour.</span>"
            }
        }

        if (masterPresenceStatusConfigured()) {
            section("<b>Third Party Services config</b>") {
                paragraph "Optional whole-house or system-level presence evidence from Google Home, Alexa, SmartThings or similar services mirrored into Hubitat."
                input "houseEvidenceSwitches", "capability.switch",
                    title: "Third Party Services - Google Home, Alexa, SmartThings, etc.",
                    description: "Optional. on = home evidence, off = departed/away evidence. Use this for platform-level services, not per-person evidence.",
                    multiple: true,
                    required: false,
                    submitOnChange: true

            }

            section("<b>Weighting and stale evidence</b>") {
                paragraph "Default weighting favours local evidence without making one third-party service authoritative. IP is strong because it proves the phone is on the LAN, but it is still imperfect because a phone can be left at home."
                input "arrivalDelaySeconds", "number",
                    title: "Arrival delay in seconds",
                    defaultValue: 0,
                    required: false,
                    submitOnChange: true

                input "departureDelayMinutes", "number",
                    title: "Departure delay in minutes",
                    description: "Recommended: 5-15",
                    defaultValue: 10,
                    required: false,
                    submitOnChange: true

                input "personPresentThreshold", "number",
                    title: "Person present score threshold",
                    description: "Recommended: 60",
                    defaultValue: 60,
                    required: true,
                    submitOnChange: true

                input "housePresentThreshold", "number",
                    title: "Third Party Services score threshold",
                    description: "Recommended: 60",
                    defaultValue: 60,
                    required: true,
                    submitOnChange: true

                input "ipWeight", "number",
                    title: "IP reachable weight",
                    description: "Recommended: 70",
                    defaultValue: 70,
                    required: true,
                    submitOnChange: true

                input "mobilePresenceWeight", "number",
                    title: "Hubitat mobile geolocation presence weight",
                    description: "Recommended: 60",
                    defaultValue: 60,
                    required: true,
                    submitOnChange: true

                input "houseSwitchWeight", "number",
                    title: "Third Party Services switch weight",
                    description: "Recommended: 60 for Google Home, Alexa or SmartThings services",
                    defaultValue: 60,
                    required: true,
                    submitOnChange: true

                input "staleEvidenceHours", "number",
                    title: "Ignore non-IP evidence after this many hours without an event",
                    description: "Recommended: 12 hours",
                    defaultValue: 12,
                    required: true,
                    submitOnChange: true
            }

            section("<b>IP configuration</b>") {
                input "requireLocalIpSubnet", "bool",
                    title: "Require IP addresses to be on the same local /24 network as this Hubitat hub",
                    description: "Recommended on. Turn off only for routed VLAN/subnet designs Hubitat can reach.",
                    defaultValue: true,
                    required: false,
                    submitOnChange: true

                input "pingIntervalSeconds", "enum",
                    title: "IP check interval",
                    description: "Recommended: 5 minutes for phones.",
                    options: ["30":"30 seconds", "60":"1 minute", "120":"2 minutes", "300":"5 minutes", "600":"10 minutes"],
                    defaultValue: "300",
                    required: true,
                    submitOnChange: true

                input "pingCount", "enum",
                    title: "Ping count per check",
                    options: ["1":"1 ping", "2":"2 pings", "3":"3 pings", "4":"4 pings"],
                    defaultValue: "2",
                    required: true,
                    submitOnChange: true

                input "ipFailureThreshold", "number",
                    title: "Consecutive failed IP checks before an IP counts as away",
                    description: "Recommended: 2-5 when using 5-minute checks.",
                    defaultValue: 2,
                    required: true,
                    submitOnChange: true

                input "pingNowBtn", "button", title: "Ping now", submitOnChange: true
            }

            section("<b>Diagnostic Mode</b>") {
                paragraph decisionDetailHtml()

                input "historyLimit", "number",
                    title: "Activity report rows to retain",
                    description: "Recommended: 500",
                    defaultValue: 500,
                    required: true,
                    submitOnChange: true

                input "debugLogging", "bool",
                    title: "Enable debug logging for 30 minutes",
                    defaultValue: false,
                    required: false,
                    submitOnChange: true

            }
        }
    }
}

def peoplePage(params = null) {
    initialiseState()
    return dynamicPage(name: "peoplePage", title: "User Configuration", install: false, uninstall: false) {
        section("<b>Status dashboard</b>") {
            href(name: "mainPageLinkFromPeople", title: "Back to status dashboard", description: "Return to Evaluation Status Summary and Guest Mode controls.", page: "mainPage")
            href(name: "advancedPageLinkFromPeople", title: "Advanced Configuration", description: "Third Party Services, weighting, IP, notifications and diagnostics.", page: "advancedConfigPage")
        }

            section("<b>People</b>") {
                paragraph "Add household members here. Use the Hubitat mobile app geolocation presence sensor as the primary signal. The phone IP is optional secondary evidence. Saved users remain on this page with Edit and Delete actions."
                Integer count = personCountValue()

                for (int i = 1; i <= count; i++) {
                    Boolean created = personCreatedValue(i)
                    Boolean editing = personEditingValue(i)
                    paragraph "<hr/><b>Person ${i}</b>"

                    if (created && !editing) {
                        paragraph personSavedSummaryHtml(i)
                        input "editPersonBtn${i}", "button",
                            title: "Edit Person ${i}",
                            submitOnChange: true
                        if (i > 1) {
                            input "deletePersonBtn${i}", "button",
                                title: "Delete Person ${i}",
                                submitOnChange: true
                        }
                    } else {
                        String nameSetting = personNameInputKey(i)
                        String presenceSetting = personPresenceInputKey(i)
                        String ipSetting = personIpInputKey(i)

                        input nameSetting, "text",
                            title: "Person ${i} name",
                            description: "Please enter known name.",
                            required: false,
                            submitOnChange: true

                        input ipSetting, "text",
                            title: "Phone IP address for person ${i}",
                            description: "Optional. One reserved phone IP address only. It is tested automatically after saving the person.",
                            required: false,
                            submitOnChange: true

                        paragraph personIpValidationHtml(i)
                        input presenceSetting, "capability.presenceSensor",
                            title: "Hubitat mobile app geolocation presence sensor for person ${i}",
                            description: "Select the Hubitat mobile app phone geolocation device. Presence is validated when the person is saved.",
                            multiple: false,
                            required: false,
                            submitOnChange: false

                        paragraph personPresenceValidationHtml(i)

                        if (personHasAnyVisibleConfiguration(i)) {
                            input "savePersonBtn${i}", "button",
                                title: personSaveButtonLabel(i),
                                submitOnChange: true
                            if (editing) {
                                input "cancelEditPersonBtn${i}", "button",
                                    title: "Cancel Edit",
                                    submitOnChange: true
                            }
                        } else {
                            paragraph "Capture this person's details, then save the person."
                            if (editing) {
                                input "cancelEditPersonBtn${i}", "button",
                                    title: "Cancel Edit",
                                    submitOnChange: true
                            }
                        }
                    }
                }

                if (peopleReadyForAdd()) {
                    if (count < maxPeople()) {
                        input "addPersonBtn", "button",
                            title: "Add another user",
                            submitOnChange: true
                    } else {
                        paragraph "Maximum of ${maxPeople()} people configured."
                    }
                }
            }
    }
}

// ---------------- Lifecycle ----------------

void initialize(Boolean fromDeferredSetup = false) {
    initialiseState()

    // Do not automatically create the application main status child switch.
    // It is a deliberate stage-gate selected/created by the user first.
    if (!masterPresenceStatusConfigured()) {
        atomicState.lastDecision = "Waiting for the application main status switch to be created or assigned"
        atomicState.lastPingScheduled = "Blocked until the application main status switch is configured"
        logInfo(atomicState.lastDecision)
        return
    }

    // Keep the page action fast. Guest switch creation and first evaluation are deferred
    // so the application switch setup click does not block the UI.
    if (!fromDeferredSetup) schedulePostMasterSetup()

    subscribeEvidenceDevices()

    if (debugLogging) runIn(1800, "disableDebugLogging", [overwrite: true])

    if (allConfiguredIps()) {
        atomicState.lastPingScheduled = "First IP check scheduled at ${timestamp()}"
        runIn(2, "runPingChecks", [overwrite: true])
    } else {
        atomicState.lastPingScheduled = "No IP addresses configured"
    }

    Long until = (atomicState.guestModeUntil ?: 0L) as Long
    if (until > now()) {
        Integer seconds = Math.max(1, Math.round((until - now()) / 1000.0) as Integer)
        runIn(seconds, "expireGuestMode", [overwrite: true])
    }

    runIn(4, "evaluateFromSchedule", [overwrite: true])
    if (!fromDeferredSetup) addHistory("initialised", "Presence Manager initialised")
    ensurePendingTransitionScheduled("initialise")
    scheduleEvidenceWatchdog()
    logInfo("Initialised")
}

void subscribeEvidenceDevices() {
    List presenceDevices = allPresenceDevices()
    List switchDevices = allEvidenceSwitches()

    presenceDevices.each { dev ->
        subscribe(dev, "presence", genericEvidenceHandler)
        seedEvidenceTime(dev, "presence")
    }

    switchDevices.each { dev ->
        subscribe(dev, "switch", genericEvidenceHandler)
        seedEvidenceTime(dev, "switch")
    }

    subscribeOutputDeviceChanges()

    def gm = existingGuestModeSwitchDevice()
    if (gm) {
        subscribe(gm, "switch", guestModeSwitchHandler)
        seedEvidenceTime(gm, "switch")
    }

    atomicState.presenceSubscriptionSummary = presenceDevices ? presenceDevices.collect { it?.displayName ?: it?.id ?: "presence device" }.join(", ") : "none"
    atomicState.switchSubscriptionSummary = switchDevices ? switchDevices.collect { it?.displayName ?: it?.id ?: "switch device" }.join(", ") : "none"
    atomicState.lastSubscriptionRefresh = timestamp()
}

@SuppressWarnings("unused")
void refreshEvidenceSubscriptions() {
    unsubscribe()
    subscribeEvidenceDevices()
    addHistory("configuration", "Evidence subscriptions refreshed")
    evaluateOccupancy("evidence subscriptions refreshed")
}

void schedulePostMasterSetup() {
    if (!masterPresenceStatusConfigured()) return
    atomicState.masterSetupPending = true
    runIn(1, "postMasterPresenceStatusSetup", [overwrite: true])
}

@SuppressWarnings("unused")
void postMasterPresenceStatusSetup() {
    if (!masterPresenceStatusConfigured()) return

    createOrUpdateGuestModeChildSwitch()
    atomicState.masterSetupPending = false
    addHistory("configuration", "Deferred application switch setup completed")

    unsubscribe()
    unschedule()
    initialize(true)
}

void initialiseState() {
    if (atomicState.ipStatus == null) atomicState.ipStatus = [:]
    if (atomicState.evidenceTimes == null) atomicState.evidenceTimes = [:]
    if (atomicState.currentOccupancy == null) atomicState.currentOccupancy = "unknown"
    if (atomicState.currentConfidence == null) atomicState.currentConfidence = 0
    if (atomicState.pendingAction == null) atomicState.pendingAction = ""
    if (atomicState.pendingDueMs == null) atomicState.pendingDueMs = 0L
    if (atomicState.pendingReason == null) atomicState.pendingReason = ""
    if (atomicState.pendingConfidence == null) atomicState.pendingConfidence = 0
    if (atomicState.pendingEvidence == null) atomicState.pendingEvidence = ""
    if (atomicState.lastDecision == null) atomicState.lastDecision = "No decision yet"
    if (atomicState.lastEvaluation == null) atomicState.lastEvaluation = ""
    if (atomicState.lastEvaluationSummary == null) atomicState.lastEvaluationSummary = ""
    if (atomicState.lastChanged == null) atomicState.lastChanged = ""
    if (atomicState.lastIpValidation == null) atomicState.lastIpValidation = ""
    if (atomicState.lastPingStarted == null) atomicState.lastPingStarted = ""
    if (atomicState.lastPingScheduled == null) atomicState.lastPingScheduled = ""
    if (atomicState.guestModeUntil == null) atomicState.guestModeUntil = 0L
    if (atomicState.guestModeUntilText == null) atomicState.guestModeUntilText = ""
    if (atomicState.manualOverride == null) atomicState.manualOverride = ""
    if (atomicState.manualOverrideText == null) atomicState.manualOverrideText = ""
    if (atomicState.history == null) atomicState.history = []
    if (atomicState.personStatusSnapshot == null) atomicState.personStatusSnapshot = [:]
    if (atomicState.suppressNextGuestSwitchReportUntil == null) atomicState.suppressNextGuestSwitchReportUntil = 0L
    if (atomicState.suppressNextOutputReportUntil == null) atomicState.suppressNextOutputReportUntil = 0L
    if (atomicState.lastNotifiedOverallState == null) atomicState.lastNotifiedOverallState = ""
    if (atomicState.lastNotificationAttemptText == null) atomicState.lastNotificationAttemptText = ""
    if (atomicState.lastNotificationAttemptTime == null) atomicState.lastNotificationAttemptTime = ""
    if (atomicState.lastNotificationAttemptDevices == null) atomicState.lastNotificationAttemptDevices = 0
    if (atomicState.lastNotificationAcceptedCount == null) atomicState.lastNotificationAcceptedCount = 0
    if (atomicState.lastNotificationFailure == null) atomicState.lastNotificationFailure = ""
    if (atomicState.lastOverallCommittedState == null) atomicState.lastOverallCommittedState = ""
    if (atomicState.lastEvidenceEvent == null) atomicState.lastEvidenceEvent = ""
    if (atomicState.lastSubscriptionRefresh == null) atomicState.lastSubscriptionRefresh = ""
    if (atomicState.presenceSubscriptionSummary == null) atomicState.presenceSubscriptionSummary = "none"
    if (atomicState.switchSubscriptionSummary == null) atomicState.switchSubscriptionSummary = "none"
    if (atomicState.pendingScheduleStatus == null) atomicState.pendingScheduleStatus = ""
    initialisePersonCreationState()
    initialisePersonEditingState()
}

@SuppressWarnings("unused")
void disableDebugLogging() {
    try { app.updateSetting("debugLogging", [type: "bool", value: false]) } catch (Throwable ignored) { }
    logInfo("Debug logging disabled")
}

void subscribeOutputDeviceChanges() {
    def target = null
    try { target = outputDevice() } catch (Throwable ignored) { }
    if (!target) return

    try {
        subscribe(target, "switch", outputDeviceSwitchHandler)
        seedEvidenceTime(target, "switch")
    } catch (Throwable ignored) { }

    try {
        subscribe(target, "presence", outputDevicePresenceHandler)
        seedEvidenceTime(target, "presence")
    } catch (Throwable ignored) { }
}

void outputDeviceSwitchHandler(evt) {
    String value = (evt?.value ?: "").toString().toLowerCase()
    if (value == "on") externalOutputStateChanged("occupied", "main status switch changed to on")
    else if (value == "off") externalOutputStateChanged("empty", "main status switch changed to off")
}

void outputDevicePresenceHandler(evt) {
    String value = (evt?.value ?: "").toString().toLowerCase()
    if (value == "present") externalOutputStateChanged("occupied", "main status presence changed to present")
    else if (value == "not present" || value == "not_present" || value == "notpresent") externalOutputStateChanged("empty", "main status presence changed to not present")
}

void externalOutputStateChanged(String state, String reason) {
    Boolean suppressReport = (((atomicState.suppressNextOutputReportUntil ?: 0L) as Long) > now())
    if (suppressReport) {
        atomicState.suppressNextOutputReportUntil = 0L
        logDebug("Suppressed app-driven main status event: ${state} - ${reason}")
        return
    }

    String normalised = (state ?: "").toString().toLowerCase()
    if (!(normalised in ["occupied", "empty"])) return

    // B3.1.40: the main status/output device is an output mirror, not authoritative evidence.
    // An external off/not-present event on that device must not force Overall Away while any person
    // still has valid presence evidence. Re-run the normal decision engine instead so all user/IP,
    // third-party and Guest Mode evidence is respected, and departure delays still apply.
    atomicState.lastDecision = "External main status change observed (${normalised}) - re-evaluating evidence instead of forcing state: ${reason}"
    logInfo(atomicState.lastDecision)
    evaluateOccupancy("external main status change: ${reason}")
}

// ---------------- Event handlers ----------------

void genericEvidenceHandler(evt) {
    updateEvidenceTime(evt?.device, evt?.name)
    atomicState.lastEvidenceEvent = "${timestamp()} - ${evt?.device?.displayName ?: 'device'} ${evt?.name ?: 'attribute'}=${evt?.value ?: ''}"
    logDebug("Evidence event: ${evt?.device?.displayName} ${evt?.name}=${evt?.value}")
    evaluateOccupancy("evidence event: ${evt?.device?.displayName} ${evt?.value}")
}

void guestModeSwitchHandler(evt) {
    updateEvidenceTime(evt?.device, evt?.name)
    String value = (evt?.value ?: "").toString()
    Boolean suppressReport = (((atomicState.suppressNextGuestSwitchReportUntil ?: 0L) as Long) > now())

    if (value == "off") {
        atomicState.guestModeUntil = 0L
        atomicState.guestModeUntilText = ""
        unschedule("expireGuestMode")
        if (!suppressReport) {
            addHistory("guest mode", "Guest Mode ended from child switch")
            notifyAll("Guest Mode Off")
        }
    } else if (value == "on") {
        if (!suppressReport) {
            Integer hours = guestModeDurationHoursValue()
            atomicState.guestModeUntil = now() + (hours * 60L * 60L * 1000L)
            atomicState.guestModeUntilText = formatEpochMs((atomicState.guestModeUntil ?: 0L) as Long)
            Integer seconds = Math.max(1, hours * 60 * 60)
            runIn(seconds, "expireGuestMode", [overwrite: true])
            addHistory("guest mode", "Guest Mode started from child switch until ${atomicState.guestModeUntilText}")
            notifyAll(guestModeOnNotificationText())
        }
    }

    if (suppressReport) atomicState.suppressNextGuestSwitchReportUntil = 0L
    logDebug("Guest Mode switch event: ${evt?.device?.displayName} ${evt?.name}=${evt?.value}")
    evaluateOccupancy("Guest Mode switch ${value}")
}

@SuppressWarnings("unused")
void evaluateFromSchedule() {
    evaluateOccupancy("scheduled evaluation")
}

void scheduleEvidenceWatchdog() {
    if (!masterPresenceStatusConfigured()) return
    Integer seconds = evidenceWatchdogSecondsValue()
    if (seconds <= 0) return
    runIn(seconds, "evaluateEvidenceWatchdog", [overwrite: true])
}

@SuppressWarnings("unused")
void evaluateEvidenceWatchdog() {
    evaluateOccupancy("evidence watchdog")
    scheduleEvidenceWatchdog()
}

void ensurePendingTransitionScheduled(String reason = "ensure pending transition") {
    String pending = (atomicState.pendingAction ?: "").toString().toLowerCase()
    if (!(pending in ["occupied", "empty"])) return

    Long dueMs = pendingDueMsValue()
    if (dueMs <= 0L) return

    Long remainingMs = dueMs - now()
    if (remainingMs <= 0L) {
        atomicState.pendingScheduleStatus = "Pending ${pending} is due; watchdog queued at ${timestamp()} - ${reason}"
        runIn(1, "pendingTransitionWatchdog", [overwrite: true])
        return
    }

    Integer seconds = Math.max(1, Math.ceil((remainingMs as Double) / 1000D) as Integer)
    if (pending == "occupied") runIn(seconds, "setOccupiedScheduled", [overwrite: true])
    else runIn(seconds, "setEmptyScheduled", [overwrite: true])

    runIn(seconds + 5, "pendingTransitionWatchdog", [overwrite: true])
    atomicState.pendingScheduleStatus = "Pending ${pending} ensured for ${seconds} seconds at ${timestamp()} - ${reason}"
}

@SuppressWarnings("unused")
void pendingTransitionWatchdog() {
    if (!processExpiredPendingActionIfDue("pending transition watchdog")) {
        ensurePendingTransitionScheduled("pending transition watchdog")
    }
}

Boolean processExpiredPendingActionIfDue(String reason = "expired pending action") {
    String pending = (atomicState.pendingAction ?: "").toString().toLowerCase()
    if (!(pending in ["occupied", "empty"])) return false

    Long dueMs = pendingDueMsValue()
    if (dueMs <= 0L || now() < dueMs) return false

    if (state.processingExpiredPendingAction == true) return false
    state.processingExpiredPendingAction = true
    try {
        atomicState.pendingScheduleStatus = "Processing expired pending ${pending} at ${timestamp()} - ${reason}"
        if (pending == "occupied") setOccupiedScheduled()
        else setEmptyScheduled()
        return true
    } finally {
        state.processingExpiredPendingAction = false
    }
}

// ---------------- IP ping ----------------

@SuppressWarnings("unused")
void runPingChecks() {
    // Scheduled and normal automatic checks retain the configured failure threshold.
    runPingChecksInternal(false, "IP check")
}

void runManualDashboardRefresh() {
    // This is deliberately separate from the scheduled handler. It runs the same
    // complete ping sweep but evaluates the just-completed result immediately,
    // including a first failed sweep, for the manual dashboard action only.
    runPingChecksInternal(true, "manual dashboard refresh")
    markManualDashboardSnapshot()
}

void runPingChecksInternal(Boolean manualSingleSweepIp, String reason) {
    List<String> ips = allConfiguredIps()
    atomicState.lastPingStarted = timestamp()

    if (!ips) {
        atomicState.ipStatus = [:]
        atomicState.lastPingScheduled = "No IP addresses configured"
        evaluateOccupancy(reason, manualSingleSweepIp == true)
        return
    }

    Map status = (atomicState.ipStatus ?: [:]) as Map
    Map newStatus = [:]

    ips.each { String ip ->
        Map row = pingIpAddress(ip, (status[ip] ?: [:]) as Map)
        newStatus[ip] = row
    }

    atomicState.ipStatus = newStatus
    evaluateOccupancy(reason, manualSingleSweepIp == true)
    scheduleNextPing()
}

void markManualDashboardSnapshot() {
    // Hubitat dynamic pages can render more than once around a button action. A
    // short timestamp window is repeat-safe, unlike a one-use Boolean flag that
    // could be consumed by an intermediate render before the table is displayed.
    atomicState.manualDashboardSnapshotUntilMs = now() + 30000L
}

Boolean manualDashboardSnapshotActive() {
    Long untilMs = (atomicState.manualDashboardSnapshotUntilMs ?: 0L) as Long
    return untilMs > 0L && now() <= untilMs
}

Map pingIpAddress(String ip, Map existingRow = [:]) {
    Map row = (existingRow ?: [:]) as Map
    Boolean reachable = false
    String packetLoss = ""
    String errorText = ""
    String raw = ""
    Integer packetsReceived = null
    Integer packetsTransmitted = null
    Integer averageMs = null

    try {
        def pingData = hubitat.helper.NetworkUtils.ping(ip, pingCountValue())
        raw = pingData?.toString() ?: ""

        Map parsed = parsePingOutcome(pingData, raw)
        reachable = parsed.reachable == true
        packetLoss = (parsed.packetLoss ?: "").toString()
        packetsReceived = parsed.packetsReceived instanceof Integer ? parsed.packetsReceived as Integer : null
        packetsTransmitted = parsed.packetsTransmitted instanceof Integer ? parsed.packetsTransmitted as Integer : null
        averageMs = parsed.averageMs instanceof Integer ? parsed.averageMs as Integer : null

        if (!parsed.confident) errorText = "Ping result parsed with low confidence"
    } catch (Throwable t) {
        reachable = false
        errorText = safeMessage(t.message)
    }

    row.ip = ip
    row.lastChecked = timestamp()
    row.lastCheckedMs = now()
    row.packetLoss = packetLoss
    row.packetsReceived = packetsReceived
    row.packetsTransmitted = packetsTransmitted
    row.averageMs = averageMs
    row.lastReachable = reachable
    row.lastResult = reachable ? "reachable" : "unreachable"
    row.error = errorText
    row.raw = raw

    if (reachable) {
        row.failureCount = 0
        row.lastSuccess = timestamp()
        row.lastSuccessMs = now()
    } else {
        row.failureCount = ((row.failureCount ?: 0) as Integer) + 1
        row.lastFailure = timestamp()
    }

    logDebug("Ping ${ip}: result=${row.lastResult}, rx=${packetsReceived}, tx=${packetsTransmitted}, loss=${packetLoss ?: 'unknown'}, avgMs=${averageMs}, failures=${row.failureCount}, error=${errorText}, raw=${raw}")
    return row
}

void scheduleNextPing() {
    if (!allConfiguredIps()) {
        atomicState.lastPingScheduled = "No IP addresses configured"
        return
    }

    Integer seconds = pingIntervalValue()
    atomicState.lastPingScheduled = "Next IP check scheduled in ${seconds} seconds at ${timestamp()}"
    runIn(seconds, "runPingChecks", [overwrite: true])
}

// ---------------- Decision engine ----------------

void evaluateOccupancy(String reason = "evaluation", Boolean manualSingleSweepIp = false) {
    initialiseState()

    if (processExpiredPendingActionIfDue("before ${reason}")) return

    Map result = evaluateAllEvidence(manualSingleSweepIp == true)
    atomicState.lastEvaluation = timestamp()
    atomicState.lastEvaluationSummary = result.summary ?: ""
    recordUserStatusChanges(result)
    recordThirdPartyStatusChanges(result)

    if (manualOverrideActive()) {
        atomicState.pendingAction = ""
        unschedule("setOccupiedScheduled")
        unschedule("setEmptyScheduled")
        String override = manualOverrideValue()
        if (override == "occupied") setOccupied("manual Force Home override - ${reason}", 100, "Manual Override")
        else if (override == "empty") setEmpty("manual Force Away override - ${reason}", 0, "Manual Override")
        return
    }

    if (result.guestModeActive == true) {
        atomicState.pendingAction = ""
        unschedule("setEmptyScheduled")
        scheduleOccupied("Guest Mode active - ${reason}", 100, "Guest Mode")
        return
    }

    if (result.hasConfiguredPresenceSources != true) {
        atomicState.pendingAction = ""
        unschedule("setOccupiedScheduled")
        unschedule("setEmptyScheduled")
        atomicState.currentOccupancy = "pending_config"
        atomicState.currentConfidence = 0
        atomicState.lastDecision = "Pending user and 3rd party systems configuration - ${reason}"
        atomicState.lastChanged = timestamp()
        atomicState.lastNotifiedOverallState = ""
        logDebug(atomicState.lastDecision)
        return
    }

    String committed = committedOccupancyState()

    if (result.occupied == true) {
        atomicState.pendingAction = ""
        atomicState.pendingDueMs = 0L
        unschedule("setEmptyScheduled")
        unschedule("pendingTransitionWatchdog")
        if (committed == "occupied") {
            atomicState.lastDecision = "Holding Home - ${reason}; ${result.summary}"
            logDebug(atomicState.lastDecision)
        } else {
            scheduleOccupied(reason, result.confidence as Integer, result.bestEvidence ?: "")
        }
        return
    }

    if (result.allAbsent == true) {
        unschedule("setOccupiedScheduled")
        if (committed == "empty") {
            atomicState.pendingAction = ""
            atomicState.pendingDueMs = 0L
            unschedule("setEmptyScheduled")
            unschedule("pendingTransitionWatchdog")
            atomicState.lastDecision = "Holding Away - ${reason}; ${result.summary}"
            logDebug(atomicState.lastDecision)
        } else {
            scheduleEmpty(reason, result.confidence as Integer, result.bestEvidence ?: "")
        }
        return
    }

    // Defensive fallback only: with the current evaluateAllEvidence() formulas,
    // result.occupied and result.allAbsent are exact complements of each other
    // (occupied == false always implies allAbsent == true), so this branch cannot
    // currently be reached. Left in place in case a future change to the evidence
    // model introduces a genuine "mixed" state, rather than falling through silently.
    atomicState.pendingAction = ""
    atomicState.lastDecision = "Holding previous state - ${reason}; ${result.summary}"
    addHistory("hold", atomicState.lastDecision)
    logDebug(atomicState.lastDecision)
}

Map evaluateAllEvidence(Boolean manualSingleSweepIp = false) {
    List<Map> people = personProfiles().collect { evaluatePerson(it as Map, manualSingleSweepIp == true) }
    Integer bestPersonScore = people ? (people.collect { (it.score ?: 0) as Integer }.max() as Integer) : 0
    List presentPeople = people.findAll { (it.present == true) }

    Map household = evaluateHouseholdEvidence()
    Map guest = evaluateGuestEvidence()
    Boolean gmActive = guest.modeActive == true

    Integer confidence = [bestPersonScore, household.score ?: 0, guest.score ?: 0].max() as Integer
    Boolean occupied = gmActive || presentPeople || household.present == true || guest.present == true

    Boolean allPeopleAbsent = people ? people.every { it.present != true } : true
    Boolean allAbsent = !gmActive && allPeopleAbsent && household.present != true && guest.present != true

    List pieces = []
    pieces << "people=${people.collect { "${it.name}:${it.score}" }.join(', ')}"
    pieces << "thirdParty=${household.score ?: 0}"
    pieces << "guest=${guest.score ?: 0}${gmActive ? ' active' : ' inactive'}"

    String bestEvidence = ""
    if (gmActive) bestEvidence = "Guest Mode"
    else if (presentPeople) bestEvidence = presentPeople.collect { "${it.name} ${it.score}" }.join(", ")
    else if (household.present) bestEvidence = "Third Party Services"

    return [
        occupied: occupied,
        allAbsent: allAbsent,
        confidence: confidence,
        people: people,
        household: household,
        guest: guest,
        guestModeActive: gmActive,
        hasConfiguredPresenceSources: (people || asList(houseEvidenceSwitches)) ? true : false,
        bestEvidence: bestEvidence,
        summary: pieces.join(" | ")
    ]
}

Map evaluatePerson(Map profile, Boolean manualSingleSweepIp = false) {
    String name = profile.name ?: "Person ${profile.index}"
    Integer score = 0
    List evidence = []
    List ignored = []

    List presenceDevices = profile.presenceDevices ?: []
    Boolean mobilePresent = false
    presenceDevices.each { dev ->
        Boolean stale = isEvidenceStale(dev, "presence")
        String value = safeCurrentValue(dev, "presence")
        if (value == "present" && !stale) {
            mobilePresent = true
            evidence << "${dev.displayName}=present"
        } else if (value == "present" && stale) {
            ignored << "${dev.displayName}=present stale"
        }
    }
    if (mobilePresent) score += weightMobile()

    Boolean ipReachable = false
    (profile.ips ?: []).each { String ip ->
        Map row = ((atomicState.ipStatus ?: [:]) as Map)[ip] as Map
        if (manualSingleSweepIp == true) {
            // For the Manual Refresh data action, use the most recent full sweep
            // exactly as it completed. A failed sweep contributes no IP presence
            // evidence, even when the normal failure threshold would still carry
            // forward a prior success.
            if (row?.lastReachable == true) {
                ipReachable = true
                evidence << "${ip}=reachable (manual sweep)"
            } else if (row?.lastResult == "unreachable") {
                ignored << "${ip}=unreachable (manual sweep)"
            }
        } else if (ipIsReachableAndFresh(row)) {
            ipReachable = true
            evidence << "${ip}=reachable"
        } else if (row?.lastReachable == true) {
            ignored << "${ip}=reachable stale"
        }
    }
    if (ipReachable) score += weightIp()

    score = Math.min(100, score)
    Boolean present = score >= personThreshold()

    return [
        index: profile.index,
        name: name,
        presenceDevices: profile.presenceDevices ?: [],
        score: score,
        present: present,
        evidence: evidence,
        ignored: ignored,
        ips: profile.ips ?: []
    ]
}

Map evaluateHouseholdEvidence() {
    Integer score = 0
    List evidence = []
    List ignored = []
    List providers = []

    List switches = asList(houseEvidenceSwitches)
    Boolean anyOn = false

    switches.each { dev ->
        Boolean stale = isEvidenceStale(dev, "switch")
        String value = safeCurrentValue(dev, "switch") ?: "unknown"
        Boolean effectiveHome = (value == "on" && !stale)
        providers << [
            key: deviceHistoryKey(dev),
            name: dev?.displayName ?: "Third Party Provider",
            status: effectiveHome ? "Home" : "Departed"
        ]

        if (value == "on" && !stale) {
            anyOn = true
            evidence << "${dev.displayName}=on"
        } else if (value == "on" && stale) {
            ignored << "${dev.displayName}=on stale"
        }
    }

    if (anyOn) score += weightHouseSwitch()
    score = Math.min(100, score)

    return [
        score: score,
        present: score >= houseThreshold(),
        evidence: evidence,
        ignored: ignored,
        providers: providers
    ]
}

Map evaluateGuestEvidence() {
    Boolean mode = guestModeActive()
    Integer score = mode ? 100 : 0

    return [
        score: score,
        present: mode,
        active: mode,
        modeActive: mode
    ]
}

void scheduleOccupied(String reason, Integer confidence, String evidence) {
    Integer delay = arrivalDelayValue()
    if (delay <= 0) {
        atomicState.pendingAction = ""
        atomicState.pendingDueMs = 0L
        unschedule("setOccupiedScheduled")
        unschedule("pendingTransitionWatchdog")
        setOccupied(reason, confidence, evidence)
        return
    }

    String pending = (atomicState.pendingAction ?: "").toString().toLowerCase()
    Long dueMs = pendingDueMsValue()
    if (pending == "occupied" && dueMs > now()) {
        ensurePendingTransitionScheduled("occupied already pending")
        logDebug("Occupied already scheduled since ${atomicState.pendingSince}")
        return
    }
    if (pending == "occupied" && dueMs > 0L && dueMs <= now()) {
        processExpiredPendingActionIfDue("occupied already due")
        return
    }

    atomicState.pendingAction = "occupied"
    atomicState.pendingDueMs = now() + (delay * 1000L)
    atomicState.pendingReason = reason
    atomicState.pendingConfidence = confidence
    atomicState.pendingEvidence = evidence
    atomicState.pendingSince = timestamp()
    atomicState.lastDecision = "Scheduling occupied in ${delay} seconds - ${reason}"
    addHistory("pending occupied", atomicState.lastDecision)
    runIn(delay, "setOccupiedScheduled", [overwrite: true])
    runIn(delay + 5, "pendingTransitionWatchdog", [overwrite: true])
}

void scheduleEmpty(String reason, Integer confidence, String evidence) {
    Integer delay = departureDelaySecondsValue()
    if (delay <= 0) {
        atomicState.pendingAction = ""
        atomicState.pendingDueMs = 0L
        unschedule("setEmptyScheduled")
        unschedule("pendingTransitionWatchdog")
        setEmpty(reason, confidence, evidence)
        return
    }

    String pending = (atomicState.pendingAction ?: "").toString().toLowerCase()
    Long dueMs = pendingDueMsValue()
    if (pending == "empty" && dueMs > now()) {
        ensurePendingTransitionScheduled("empty already pending")
        logDebug("Empty already scheduled since ${atomicState.pendingSince}")
        return
    }
    if (pending == "empty" && dueMs > 0L && dueMs <= now()) {
        processExpiredPendingActionIfDue("empty already due")
        return
    }

    atomicState.pendingAction = "empty"
    atomicState.pendingDueMs = now() + (delay * 1000L)
    atomicState.pendingReason = reason
    atomicState.pendingConfidence = confidence
    atomicState.pendingEvidence = evidence
    atomicState.pendingSince = timestamp()
    atomicState.lastDecision = "Scheduling empty in ${delay} seconds - ${reason}"
    addHistory("pending empty", atomicState.lastDecision)
    runIn(delay, "setEmptyScheduled", [overwrite: true])
    runIn(delay + 5, "pendingTransitionWatchdog", [overwrite: true])
}

@SuppressWarnings("unused")
void setOccupiedScheduled() {
    setOccupied((atomicState.pendingReason ?: "scheduled occupied").toString(), (atomicState.pendingConfidence ?: 0) as Integer, (atomicState.pendingEvidence ?: "").toString())
}

@SuppressWarnings("unused")
void setEmptyScheduled() {
    Map result = evaluateAllEvidence()
    if (result.occupied == true || result.guestModeActive == true) {
        atomicState.pendingAction = ""
        atomicState.pendingDueMs = 0L
        atomicState.lastDecision = "Cancelled empty - presence returned before departure delay expired"
        addHistory("cancel empty", atomicState.lastDecision)
        logInfo(atomicState.lastDecision)
        return
    }

    if (result.hasConfiguredPresenceSources != true) {
        atomicState.pendingAction = ""
        atomicState.pendingDueMs = 0L
        atomicState.currentOccupancy = "pending_config"
        atomicState.currentConfidence = 0
        atomicState.lastDecision = "Cancelled empty - pending user and 3rd party systems configuration"
        atomicState.lastNotifiedOverallState = ""
        logInfo(atomicState.lastDecision)
        return
    }

    if (result.allAbsent == true) {
        setEmpty((atomicState.pendingReason ?: "scheduled empty").toString(), (result.confidence ?: 0) as Integer, (result.bestEvidence ?: "").toString())
    } else {
        atomicState.pendingAction = ""
        atomicState.pendingDueMs = 0L
        atomicState.lastDecision = "Cancelled empty - not all evidence is absent"
        addHistory("cancel empty", atomicState.lastDecision)
        logInfo(atomicState.lastDecision)
    }
}

void setOccupied(String reason, Integer confidence = 100, String evidence = "", Boolean forceNotify = false) {
    atomicState.pendingAction = ""
    atomicState.pendingDueMs = 0L
    unschedule("setEmptyScheduled")
    unschedule("pendingTransitionWatchdog")

    def target = outputDevice()
    if (!target) {
        atomicState.lastDecision = "Cannot set occupied - no application main status target configured"
        addHistory("error", atomicState.lastDecision)
        logWarn(atomicState.lastDecision)
        return
    }

    atomicState.suppressNextOutputReportUntil = now() + 5000L
    if (!commandOutputOccupied(target)) {
        logWarn("Could not mark main status target occupied. Device may not expose a supported command.")
    }

    setOutputReason(target, "Occupied: ${reason}")
    setOutputAttributes(target, "occupied", confidence, evidence, reason)

    String previousCommitted = committedOccupancyState()
    Boolean changed = previousCommitted != "occupied"
    atomicState.currentOccupancy = "occupied"
    atomicState.currentConfidence = confidence
    atomicState.lastOverallCommittedState = "occupied"
    atomicState.lastDecision = "Occupied - ${reason}"
    atomicState.lastChanged = timestamp()

    if (changed) addHistory("home status", "Overall home status: Home")
    notifyOverallStatusChange("occupied", forceNotify, changed)
    logInfo(atomicState.lastDecision)
}

void setEmpty(String reason, Integer confidence = 0, String evidence = "", Boolean forceNotify = false) {
    atomicState.pendingAction = ""
    atomicState.pendingDueMs = 0L
    unschedule("setOccupiedScheduled")
    unschedule("pendingTransitionWatchdog")

    if (shouldGuardEmptyCommit(reason, forceNotify)) {
        Map latest = evaluateAllEvidence()
        if (latest.hasConfiguredPresenceSources != true) {
            atomicState.currentOccupancy = "pending_config"
            atomicState.currentConfidence = 0
            atomicState.lastDecision = "Blocked Away - pending user and 3rd party systems configuration"
            atomicState.lastNotifiedOverallState = ""
            addHistory("home status", "Overall away blocked: pending user and 3rd party systems configuration")
            logInfo(atomicState.lastDecision)
            return
        }
        if (latest.occupied == true || latest.guestModeActive == true || latest.allAbsent != true) {
            String evidenceText = (latest.bestEvidence ?: latest.summary ?: "presence still detected").toString()
            atomicState.lastDecision = "Blocked Away - presence still detected: ${evidenceText}"
            atomicState.lastChanged = timestamp()
            addHistory("home status", "Overall away blocked: ${evidenceText}")
            logInfo(atomicState.lastDecision)
            return
        }
    }

    def target = outputDevice()
    if (!target) {
        atomicState.lastDecision = "Cannot set empty - no application main status target configured"
        addHistory("error", atomicState.lastDecision)
        logWarn(atomicState.lastDecision)
        return
    }

    atomicState.suppressNextOutputReportUntil = now() + 5000L
    if (!commandOutputEmpty(target)) {
        logWarn("Could not mark main status target empty. Device may not expose a supported command.")
    }

    setOutputReason(target, "Empty: ${reason}")
    setOutputAttributes(target, "empty", confidence, evidence, reason)

    String previousCommitted = committedOccupancyState()
    Boolean changed = previousCommitted != "empty"
    atomicState.currentOccupancy = "empty"
    atomicState.currentConfidence = confidence
    atomicState.lastOverallCommittedState = "empty"
    atomicState.lastDecision = "Empty - ${reason}"
    atomicState.lastChanged = timestamp()

    if (changed) addHistory("home status", "Overall home status: Departed")
    notifyOverallStatusChange("empty", forceNotify, changed)
    logInfo(atomicState.lastDecision)
}

Boolean shouldGuardEmptyCommit(String reason, Boolean forceNotify = false) {
    if (manualOverrideActive()) return false
    String r = (reason ?: "").toString().toLowerCase()
    if (r.contains("manual force away")) return false
    return true
}

// ---------------- Manual occupancy override ----------------

void forceOverrideHome() {
    atomicState.manualOverride = "occupied"
    atomicState.manualOverrideText = "Force Home active since ${timestamp()}"
    atomicState.pendingAction = ""
    unschedule("setOccupiedScheduled")
    unschedule("setEmptyScheduled")
    addHistory("manual override", "Force Home override enabled")
    setOccupied("manual Force Home override", 100, "Manual Override", true)
}

void forceOverrideAway() {
    atomicState.manualOverride = "empty"
    atomicState.manualOverrideText = "Force Away active since ${timestamp()}"
    atomicState.pendingAction = ""
    unschedule("setOccupiedScheduled")
    unschedule("setEmptyScheduled")
    addHistory("manual override", "Force Away override enabled")
    setEmpty("manual Force Away override", 0, "Manual Override", true)
}

void removeForceOverride() {
    if (!manualOverrideActive()) {
        atomicState.lastDecision = "No manual force override to remove"
        addHistory("manual override", "Remove Force pressed - no active override")
        evaluateOccupancy("manual override removal requested")
        return
    }

    String previous = manualOverrideStatusText()
    atomicState.manualOverride = ""
    atomicState.manualOverrideText = ""
    atomicState.pendingAction = ""
    unschedule("setOccupiedScheduled")
    unschedule("setEmptyScheduled")
    addHistory("manual override", "Force override removed - ${previous}")
    evaluateOccupancy("manual force override removed")
}

Boolean manualOverrideActive() {
    return ["occupied", "empty"].contains(manualOverrideValue())
}

String manualOverrideValue() {
    return (atomicState.manualOverride ?: "").toString().toLowerCase()
}

String manualOverrideStatusText() {
    String value = manualOverrideValue()
    if (value == "occupied") return "Force Home override active"
    if (value == "empty") return "Force Away override active"
    return "none"
}

// ---------------- Guest Mode ----------------

void startGuestMode() {
    if (masterPresenceStatusConfigured()) createOrUpdateGuestModeChildSwitch()
    Integer hours = guestModeDurationHoursValue()
    atomicState.guestModeUntil = now() + (hours * 60L * 60L * 1000L)
    atomicState.guestModeUntilText = formatEpochMs((atomicState.guestModeUntil ?: 0L) as Long)

    def gm = guestModeSwitchDevice()
    atomicState.suppressNextGuestSwitchReportUntil = now() + 5000L
    try { gm?.on() } catch (Throwable ignored) { }

    Integer seconds = Math.max(1, hours * 60 * 60)
    runIn(seconds, "expireGuestMode", [overwrite: true])

    addHistory("guest mode", "Guest Mode started until ${atomicState.guestModeUntilText}")
    notifyAll(guestModeOnNotificationText())
    evaluateOccupancy("Guest Mode started")
}

void cancelGuestMode() {
    if (masterPresenceStatusConfigured()) createOrUpdateGuestModeChildSwitch()
    atomicState.guestModeUntil = 0L
    atomicState.guestModeUntilText = ""
    unschedule("expireGuestMode")

    def gm = guestModeSwitchDevice()
    atomicState.suppressNextGuestSwitchReportUntil = now() + 5000L
    try { gm?.off() } catch (Throwable ignored) { }

    addHistory("guest mode", "Guest Mode ended manually")
    notifyAll("Guest Mode Off")
    evaluateOccupancy("Guest Mode cancelled")
}

@SuppressWarnings("unused")
void expireGuestMode() {
    atomicState.guestModeUntil = 0L
    atomicState.guestModeUntilText = ""

    def gm = guestModeSwitchDevice()
    atomicState.suppressNextGuestSwitchReportUntil = now() + 5000L
    try { gm?.off() } catch (Throwable ignored) { }

    addHistory("guest mode", "Guest Mode ended - timer expired")
    notifyAll("Guest Mode Off")
    evaluateOccupancy("Guest Mode expired")
}

Boolean guestModeActive() {
    Long until = (atomicState.guestModeUntil ?: 0L) as Long
    Boolean timerActive = until && until > now()
    def gm = existingGuestModeSwitchDevice()
    Boolean switchActive = gm && safeCurrentValue(gm, "switch") == "on"
    return timerActive || switchActive
}

// ---------------- Output child ----------------

void createOrUpdateOutputChild() {
    if ((outputMode ?: "child") != "child") return

    String dni = childDni()
    String label = configuredChildName()
    def child = getChildDevice(dni)

    if (!child) {
        try {
            child = addChildDevice(
                "Hubitat Integrations",
                "Presence Manager Output",
                dni,
                [
                    label: label,
                    name: "Presence Manager Output",
                    isComponent: false
                ]
            )
            logInfo("Created application main child switch: ${label}")
        } catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
            logWarn("Driver 'Presence Manager Output' is not installed. Install the driver first, then reopen this app.")
            return
        } catch (Throwable t) {
            logWarn("Could not create application main child switch: ${safeMessage(t.message)}")
            return
        }
    } else {
        try { child.setLabel(label) } catch (Throwable ignored) { }
    }
}

Boolean masterPresenceStatusConfigured() {
    String mode = (outputMode ?: "child").toString()
    if (mode == "existing") return outputSwitch != null
    if (mode == "presence") return outputPresenceDevice != null

    try {
        return getChildDevice(childDni()) != null
    } catch (Throwable ignored) {
        return false
    }
}

def outputDevice() {
    String mode = (outputMode ?: "child").toString()
    if (mode == "existing") return outputSwitch
    if (mode == "presence") return outputPresenceDevice
    try {
        return getChildDevice(childDni())
    } catch (Throwable ignored) {
        return null
    }
}

String childDni() {
    return "presence-manager-output"
}

String configuredChildName() {
    String value = (childDeviceName ?: "Presence Manager Main Status").toString().trim()
    return value ?: "Presence Manager Main Status"
}

Boolean commandOutputOccupied(target) {
    return tryOutputCommands(target, ["on", "markPresent", "arrived", "present", "arrive"])
}

Boolean commandOutputEmpty(target) {
    return tryOutputCommands(target, ["off", "markAway", "departed", "notPresent", "depart"])
}

Boolean tryOutputCommands(target, List<String> commandNames) {
    if (!target) return false

    List<String> attempted = []
    for (String commandName : (commandNames ?: [])) {
        attempted << commandName
        try {
            target."${commandName}"()
            logDebug("Output command succeeded: ${target.displayName}.${commandName}()")
            return true
        } catch (MissingMethodException ignored) {
            // Try next conventional command.
        } catch (Throwable t) {
            logDebug("Output command failed: ${target.displayName}.${commandName}() - ${safeMessage(t.message)}")
        }
    }

    logWarn("No usable output command found on ${target?.displayName}. Tried: ${attempted.join(', ')}")
    return false
}

void setOutputReason(target, String reason) {
    try { target.setReason(reason) } catch (Throwable ignored) { }
}

void setOutputAttributes(target, String occupancyValue, Integer confidence, String evidence, String reason) {
    try { target.setPresenceManagerState(occupancyValue, confidence, evidence, reason, guestModeActive()) } catch (Throwable ignored) { }
}

// ---------------- Guest Mode child switch ----------------

void createOrUpdateGuestModeChildSwitch() {
    if (!masterPresenceStatusConfigured()) return

    String dni = guestModeChildDni()
    String label = configuredGuestModeChildName()
    def child = getChildDevice(dni)

    if (!child) {
        try {
            child = addChildDevice(
                "Hubitat Integrations",
                "Presence Manager Guest Mode Switch",
                dni,
                [
                    label: label,
                    name: "Presence Manager Guest Mode Switch",
                    isComponent: false
                ]
            )
            logInfo("Created Guest Mode child switch: ${label}")
        } catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
            logWarn("Driver 'Presence Manager Guest Mode Switch' is not installed. Install the driver first, then reopen this app.")
            return
        } catch (Throwable t) {
            logWarn("Could not create Guest Mode child switch: ${safeMessage(t.message)}")
            return
        }
    } else {
        try { child.setLabel(label) } catch (Throwable ignored) { }
    }
}

def existingGuestModeSwitchDevice() {
    try {
        return getChildDevice(guestModeChildDni())
    } catch (Throwable ignored) {
        return null
    }
}

def guestModeSwitchDevice() {
    createOrUpdateGuestModeChildSwitch()
    return existingGuestModeSwitchDevice()
}

String guestModeChildDni() {
    return "presence-manager-guest-mode"
}

String guestSwitchCreatedHtml() {
    def gm = existingGuestModeSwitchDevice()
    if (gm) return "<span style='color:green;font-weight:bold;'>Guest Mode child switch: ${html(gm.displayName ?: configuredGuestModeChildName())}.</span>"
    if (atomicState.masterSetupPending) return "<span style='color:#777;font-weight:bold;'>Guest Mode child switch creation/update is queued.</span>"
    return "<span style='color:#777;'>Guest Mode child switch will be created automatically after the main status target is ready.</span>"
}

String configuredGuestModeChildName() {
    String base = configuredMasterPresenceStatusNameForGuestMode()
    if (!base) base = "Presence Manager Main Status"
    if (base.toLowerCase().endsWith("guest mode")) return base
    return "${base} Guest Mode"
}

String configuredMasterPresenceStatusNameForGuestMode() {
    String mode = (outputMode ?: "child").toString()
    if (mode == "existing" && outputSwitch) return outputSwitch.displayName?.toString()?.trim()
    if (mode == "presence" && outputPresenceDevice) return outputPresenceDevice.displayName?.toString()?.trim()
    return configuredChildName()
}

// ---------------- People and input collection ----------------

Integer maxPeople() { return 10 }

String personSaveButtonLabel(Integer index) {
    return "Save Person ${index}"
}

Integer personSaveIndexFromButton(String btn) {
    try {
        String suffix = (btn ?: "").replace("savePersonBtn", "")
        Integer value = suffix as Integer
        Integer count = personCountValue()
        if (value < 1 || value > count) return null
        if (personCreatedValue(value) && !personEditingValue(value)) return null
        return value
    } catch (Throwable ignored) {
        return null
    }
}

void schedulePersonIpTest(Integer index) {
    if (!index || index < 1 || index > personCountValue()) return
    if (!primaryVisibleIpForPerson(index) && !primaryConfiguredIpForPerson(index)) return

    atomicState.pendingPersonIpTestIndex = index
    atomicState.lastPingScheduled = "Automatic IP test for Person ${index} scheduled"
    runIn(2, "runPendingPersonIpTest", [overwrite: true])
}

@SuppressWarnings("unused")
void runPendingPersonIpTest() {
    Integer index = null
    try { index = (atomicState.pendingPersonIpTestIndex ?: 0) as Integer } catch (Throwable ignored) { index = 0 }
    atomicState.pendingPersonIpTestIndex = 0

    if (!index || index < 1 || index > personCountValue()) return
    testPersonIp(index)
}

void testPersonIp(Integer index) {
    if (!index || index < 1 || index > personCountValue()) return

    String ip = primaryConfiguredIpForPerson(index) ?: primaryVisibleIpForPerson(index)
    if (!ip) {
        addHistory("ip", "Automatic IP test ignored - Person ${index} has no valid accepted IP")
        return
    }

    Map status = (atomicState.ipStatus ?: [:]) as Map
    Map current = (status[ip] ?: [:]) as Map
    atomicState.lastPingStarted = timestamp()
    Map row = pingIpAddress(ip, current)
    status[ip] = row
    atomicState.ipStatus = status
    atomicState.lastPingScheduled = "Automatic IP test completed"
    addHistory("ip", "Automatically tested Person ${index} IP ${ip}: ${row.lastResult}")
    evaluateOccupancy("automatic IP test for Person ${index}")
}

String primaryConfiguredIpForPerson(Integer index) {
    List<String> ips = configuredIpsForPerson(index)
    return ips ? ips[0] : ""
}

Boolean personHasAcceptedIp(Integer index) {
    return primaryConfiguredIpForPerson(index) ? true : false
}

Integer currentDraftPersonIndex() {
    Integer count = personCountValue()
    for (int i = 1; i <= count; i++) {
        if (!personCreatedValue(i)) return i
    }
    return null
}

String personDisplayNameForIndex(Integer index) {
    String name = (settings["personName${index}"] ?: "Person ${index}").toString().trim()
    return name ?: "Person ${index}"
}

void savePerson(Integer index) {
    if (!index || index < 1 || index > personCountValue()) return
    if (!personHasAnyVisibleConfiguration(index)) {
        logDebug("Save Person ignored - Person ${index} has no captured details")
        return
    }

    if (!visiblePresenceDeviceSelectionValid(index)) {
        logDebug("Save Person ignored - Person ${index} selected device does not expose presence")
        return
    }

    Boolean wasCreated = personCreatedValue(index)
    Boolean wasEditing = personEditingValue(index)

    if (wasEditing) {
        copyVisibleEditSettingsToSavedPerson(index)
        clearEditPersonSettings(index)
        setPersonEditing(index, false)
    }

    setPersonCreated(index, true)
    String name = personDisplayNameForIndex(index)
    String historyMessage = wasCreated ? "User edited: ${name}" : "User created: ${name}"
    addHistory("person", historyMessage)
    logInfo(historyMessage)
    evaluateOccupancy("User ${name} saved")
}

void addAnotherPerson() {
    Integer count = personCountValue()
    if (count >= maxPeople()) {
        addHistory("person", "Add another user ignored - maximum ${maxPeople()} reached")
        return
    }

    if (!allVisiblePeopleCreated()) {
        addHistory("person", "Add another user ignored - current person has not been saved")
        return
    }

    Integer newCount = count + 1
    clearPersonSettings(newCount)
    setPersonCreated(newCount, false)
    app.updateSetting("personCount", [type: "enum", value: newCount.toString()])
    addHistory("person", "Opened Person ${newCount} for configuration")
}


Integer personEditIndexFromButton(String btn) {
    try {
        String suffix = (btn ?: "").replace("editPersonBtn", "")
        Integer value = suffix as Integer
        Integer count = personCountValue()
        if (value < 1 || value > count || !personCreatedValue(value)) return null
        return value
    } catch (Throwable ignored) {
        return null
    }
}

Integer personCancelEditIndexFromButton(String btn) {
    try {
        String suffix = (btn ?: "").replace("cancelEditPersonBtn", "")
        Integer value = suffix as Integer
        Integer count = personCountValue()
        if (value < 1 || value > count || !personCreatedValue(value)) return null
        return value
    } catch (Throwable ignored) {
        return null
    }
}

void editPerson(Integer index) {
    if (!index || index < 1 || index > personCountValue() || !personCreatedValue(index)) return
    copySavedPersonToEditSettings(index)
    setPersonEditing(index, true)
}

void cancelEditPerson(Integer index) {
    if (!index || index < 1 || index > personCountValue() || !personCreatedValue(index)) return
    clearEditPersonSettings(index)
    setPersonEditing(index, false)
}

Integer personIndexFromButton(String btn) {
    try {
        String suffix = (btn ?: "").replace("deletePersonBtn", "")
        Integer value = suffix as Integer
        Integer count = personCountValue()
        if (value < 2 || value > count || !personCreatedValue(value)) return null
        return value
    } catch (Throwable ignored) {
        return null
    }
}

void deletePerson(Integer index) {
    Integer count = personCountValue()
    if (!index || count <= 1 || index < 2 || index > count || !personCreatedValue(index)) return

    String deletedName = personDisplayNameForIndex(index)

    for (int i = index; i < count; i++) {
        copyPersonSettings(i + 1, i)
    }
    shiftPersonStatusSnapshotOnDelete(index, count)

    clearPersonSettings(count)
    app.updateSetting("personCount", [type: "enum", value: (count - 1).toString()])
    addHistory("person", "User deleted: ${deletedName}")
    logInfo("User deleted: ${deletedName}")
    evaluateOccupancy("User ${deletedName} deleted")
}

void copyPersonSettings(Integer fromIndex, Integer toIndex) {
    app.updateSetting("personName${toIndex}", [type: "text", value: (settings["personName${fromIndex}"] ?: "").toString()])
    app.updateSetting("ipAddressText${toIndex}", [type: "text", value: (settings["ipAddressText${fromIndex}"] ?: "").toString()])
    setPersonCreated(toIndex, personCreatedValue(fromIndex))
    setPersonEditing(toIndex, false)

    def dev = settings["presenceDevices${fromIndex}"]
    if (dev) {
        String idValue = ""
        try { idValue = dev.id?.toString() ?: "" } catch (Throwable ignored) { idValue = "" }
        if (idValue) app.updateSetting("presenceDevices${toIndex}", [type: "capability.presenceSensor", value: idValue])
        else clearPresenceDeviceSetting(toIndex)
    } else {
        clearPresenceDeviceSetting(toIndex)
    }

    clearEditPersonSettings(toIndex)
}

// Keep atomicState.personStatusSnapshot (keyed by "person<index>") aligned with the
// same index shift copyPersonSettings() performs above. Without this, the person that
// slides into a deleted slot inherits the deleted/shifted predecessor's stale
// reportedStatus / candidateStatus, causing the Activity Report to log a phantom
// Home/Departed transition for the wrong person on the next evaluation.
void shiftPersonStatusSnapshotOnDelete(Integer deletedIndex, Integer countBeforeDelete) {
    Map snapshot = (atomicState.personStatusSnapshot ?: [:]) as Map
    for (int i = deletedIndex; i < countBeforeDelete; i++) {
        String fromKey = "person${i + 1}"
        String toKey = "person${i}"
        if (snapshot.containsKey(fromKey)) snapshot[toKey] = snapshot[fromKey]
        else snapshot.remove(toKey)
    }
    snapshot.remove("person${countBeforeDelete}")
    atomicState.personStatusSnapshot = snapshot
}

void clearPersonSettings(Integer index) {
    app.updateSetting("personName${index}", [type: "text", value: ""])
    app.updateSetting("ipAddressText${index}", [type: "text", value: ""])
    clearPresenceDeviceSetting(index)
    clearEditPersonSettings(index)
    setPersonCreated(index, false)
    setPersonEditing(index, false)
}

void clearPresenceDeviceSetting(Integer index) {
    try {
        app.removeSetting("presenceDevices${index}")
    } catch (Throwable ignored) {
        try { app.updateSetting("presenceDevices${index}", [type: "capability.presenceSensor", value: null]) } catch (Throwable ignored2) { }
    }
}

void initialisePersonCreationState() {
    if (state.personCreationModelInitialised == true) return

    Integer count = personCountValue()
    Map existingMap = (state.personCreatedMap ?: atomicState.personCreatedMap ?: [:]) as Map
    Map createdMap = [:]

    for (int i = 1; i <= count; i++) {
        Object existing = existingMap[i.toString()]
        Boolean created = false
        if (existing instanceof Boolean) created = existing
        else if (existing != null) created = existing.toString().equalsIgnoreCase("true")
        else if (i > 1) created = true
        else created = personHasAnyConfiguration(i)
        createdMap[i.toString()] = created
    }

    state.personCreatedMap = createdMap
    atomicState.personCreatedMap = createdMap
    state.personCreationModelInitialised = true
    atomicState.personCreationModelInitialised = true
}

Boolean personHasAnyConfiguration(Integer index) {
    if ((settings["personName${index}"] ?: "").toString().trim()) return true
    if ((settings["ipAddressText${index}"] ?: "").toString().trim()) return true
    if (settings["presenceDevices${index}"]) return true
    return false
}

Boolean personCreatedValue(Integer index) {
    Map createdMap = (state.personCreatedMap ?: atomicState.personCreatedMap ?: [:]) as Map
    Object value = createdMap[index?.toString()]
    if (value instanceof Boolean) return value
    if (value == null) return false
    return value.toString().equalsIgnoreCase("true")
}

void setPersonCreated(Integer index, Boolean created) {
    if (!index) return
    Map createdMap = (state.personCreatedMap ?: atomicState.personCreatedMap ?: [:]) as Map
    createdMap[index.toString()] = created == true
    state.personCreatedMap = createdMap
    atomicState.personCreatedMap = createdMap
}


void initialisePersonEditingState() {
    Integer count = personCountValue()
    Map editingMap = (state.personEditingMap ?: atomicState.personEditingMap ?: [:]) as Map
    Boolean changed = false
    for (int i = 1; i <= count; i++) {
        String key = i.toString()
        if (!editingMap.containsKey(key)) {
            editingMap[key] = false
            changed = true
        }
    }
    if (changed || state.personEditingMap == null || atomicState.personEditingMap == null) {
        state.personEditingMap = editingMap
        atomicState.personEditingMap = editingMap
    }
}

Boolean personEditingValue(Integer index) {
    Map editingMap = (state.personEditingMap ?: atomicState.personEditingMap ?: [:]) as Map
    Object value = editingMap[index?.toString()]
    if (value instanceof Boolean) return value
    if (value == null) return false
    return value.toString().equalsIgnoreCase("true")
}

void setPersonEditing(Integer index, Boolean editing) {
    if (!index) return
    Map editingMap = (state.personEditingMap ?: atomicState.personEditingMap ?: [:]) as Map
    editingMap[index.toString()] = editing == true
    state.personEditingMap = editingMap
    atomicState.personEditingMap = editingMap
}

String personNameInputKey(Integer index) { return personEditingValue(index) ? "editPersonName${index}" : "personName${index}" }
String personPresenceInputKey(Integer index) { return personEditingValue(index) ? "editPresenceDevices${index}" : "presenceDevices${index}" }
String personIpInputKey(Integer index) { return personEditingValue(index) ? "editIpAddressText${index}" : "ipAddressText${index}" }

Boolean personHasAnyVisibleConfiguration(Integer index) {
    if ((settings[personNameInputKey(index)] ?: "").toString().trim()) return true
    if ((settings[personIpInputKey(index)] ?: "").toString().trim()) return true
    if (settings[personPresenceInputKey(index)]) return true
    return false
}

Boolean personHasAcceptedIpForVisibleInput(Integer index) {
    return primaryVisibleIpForPerson(index) ? true : false
}

String primaryVisibleIpForPerson(Integer index) {
    List<String> ips = validatedIpsFromText((settings[personIpInputKey(index)] ?: "").toString()).accepted
    return ips ? ips[0] : ""
}

void copySavedPersonToEditSettings(Integer index) {
    app.updateSetting("editPersonName${index}", [type: "text", value: (settings["personName${index}"] ?: "").toString()])
    app.updateSetting("editIpAddressText${index}", [type: "text", value: (settings["ipAddressText${index}"] ?: "").toString()])

    def dev = settings["presenceDevices${index}"]
    if (dev) {
        String idValue = ""
        try { idValue = dev.id?.toString() ?: "" } catch (Throwable ignored) { idValue = "" }
        if (idValue) app.updateSetting("editPresenceDevices${index}", [type: "capability.presenceSensor", value: idValue])
        else clearEditPresenceDeviceSetting(index)
    } else {
        clearEditPresenceDeviceSetting(index)
    }
}

void copyVisibleEditSettingsToSavedPerson(Integer index) {
    app.updateSetting("personName${index}", [type: "text", value: (settings["editPersonName${index}"] ?: "").toString()])
    app.updateSetting("ipAddressText${index}", [type: "text", value: (settings["editIpAddressText${index}"] ?: "").toString()])

    def dev = settings["editPresenceDevices${index}"]
    if (dev) {
        String idValue = ""
        try { idValue = dev.id?.toString() ?: "" } catch (Throwable ignored) { idValue = "" }
        if (idValue) app.updateSetting("presenceDevices${index}", [type: "capability.presenceSensor", value: idValue])
        else clearPresenceDeviceSetting(index)
    } else {
        clearPresenceDeviceSetting(index)
    }
}

void clearEditPersonSettings(Integer index) {
    app.updateSetting("editPersonName${index}", [type: "text", value: ""])
    app.updateSetting("editIpAddressText${index}", [type: "text", value: ""])
    clearEditPresenceDeviceSetting(index)
}

void clearEditPresenceDeviceSetting(Integer index) {
    try {
        app.removeSetting("editPresenceDevices${index}")
    } catch (Throwable ignored) {
        try { app.updateSetting("editPresenceDevices${index}", [type: "capability.presenceSensor", value: null]) } catch (Throwable ignored2) { }
    }
}

String personSavedSummaryHtml(Integer index) {
    String name = (settings["personName${index}"] ?: "Person ${index}").toString().trim()
    if (!name) name = "Person ${index}"
    def dev = settings["presenceDevices${index}"]
    Boolean devValid = dev ? deviceHasPresenceAttribute(dev) : false
    String devName = dev ? (dev.displayName ?: "selected") : "none configured"
    String presenceValue = devValid ? (safeCurrentValue(dev, "presence") ?: "unknown") : ""
    String ip = primaryConfiguredIpForPerson(index)
    String ipStatus = ip ? ipStatusSummaryHtml(ip, ((atomicState.ipStatus ?: [:]) as Map)[ip] as Map) : "none configured"

    String out = "<table style='border-collapse:collapse;width:100%'>"
    out += rowHtml("Name", name)
    out += rowHtmlRawValue("Geolocation device", dev ? (devValid ? "${html(devName)} = ${presenceStatusHtml(presenceValue)}" : "${html(devName)} - <span style='color:red;font-weight:bold;'>invalid presence device</span>") : html(devName))
    out += rowHtml("Configured IP", ip ?: "none configured")
    out += rowHtmlRawValue("IP status", ipStatus)
    out += "</table>"
    return out
}

Boolean anyPersonEditing() {
    Integer count = personCountValue()
    for (int i = 1; i <= count; i++) {
        if (personEditingValue(i)) return true
    }
    return false
}

Boolean peopleReadyForAdd() {
    return allVisiblePeopleCreated() && !anyPersonEditing()
}

Boolean allVisiblePeopleCreated() {
    Integer count = personCountValue()
    for (int i = 1; i <= count; i++) {
        if (!personCreatedValue(i)) return false
    }
    return true
}

List<Map> personProfiles() {
    List<Map> people = []
    Integer count = personCountValue()

    for (int i = 1; i <= count; i++) {
        if (!personCreatedValue(i)) continue
        String name = (settings["personName${i}"] ?: "Person ${i}").toString().trim()
        if (!name) name = "Person ${i}"

        List validPresenceDevices = asList(settings["presenceDevices${i}"]).findAll { dev -> deviceHasPresenceAttribute(dev) }

        people << [
            index: i,
            name: name,
            presenceDevices: validPresenceDevices,
            ips: configuredIpsForPerson(i)
        ]
    }

    return people
}

List allPresenceDevices() {
    List devices = []
    personProfiles().each { Map p -> devices.addAll(p.presenceDevices ?: []) }
    return uniqueDevices(devices)
}

List allEvidenceSwitches() {
    List devices = []
    devices.addAll(asList(houseEvidenceSwitches))
    return uniqueDevices(devices)
}

List uniqueDevices(List devices) {
    Map byId = [:]
    (devices ?: []).each { dev ->
        if (dev) byId[dev.id?.toString() ?: dev.displayName] = dev
    }
    return byId.values() as List
}

List asList(value) {
    if (!value) return []
    if (value instanceof List) return value
    return [value]
}

List<String> configuredIpsForPerson(Integer index) {
    String raw = (settings["ipAddressText${index}"] ?: "").toString()
    return validatedIpsFromText(raw).accepted
}

List<String> allConfiguredIps() {
    List<String> ips = []
    personProfiles().each { Map p -> ips.addAll(p.ips ?: []) }
    return ips.unique()
}

Map validatedIpsFromText(String raw) {
    List<String> tokens = (raw ?: "").split(/[\n,; ]+/).collect { it.trim() }.findAll { it }
    List<String> accepted = []
    List<String> invalid = []
    List<String> nonLocal = []
    List<String> duplicate = []
    List<String> extra = []
    Set<String> seenValid = new LinkedHashSet<String>()

    tokens.each { String ip ->
        if (!isValidIpv4(ip)) {
            invalid << ip
        } else if (seenValid.contains(ip)) {
            duplicate << ip
        } else if (localSubnetRequired() && !isLocalSlash24(ip)) {
            seenValid << ip
            nonLocal << ip
        } else if (accepted.size() >= 1) {
            seenValid << ip
            extra << ip
        } else {
            seenValid << ip
            accepted << ip
        }
    }

    return [accepted: accepted, invalid: invalid, nonLocal: nonLocal, duplicate: duplicate, extra: extra]
}

String personIpValidationHtml(Integer index) {
    Map v = validatedIpsFromText((settings[personIpInputKey(index)] ?: "").toString())
    if (!v.accepted && !v.invalid && !v.nonLocal && !v.duplicate && !v.extra) return ""

    String acceptedIp = ((v.accepted ?: []) ? (v.accepted[0] ?: "").toString() : "")

    String out = "<div style='font-size:0.9em'>"
    out += "<b>IP validation for person ${index}</b><br/>"
    out += "Accepted phone IP: ${html(acceptedIp ?: 'none')}<br/>"
    if (acceptedIp) out += "Last reachability check: ${personIpReachabilityHtml(acceptedIp)}<br/>"
    if (v.extra) out += "Extra IP ignored because only one IP is allowed per person: ${html(v.extra.join(', '))}<br/>"
    if (v.invalid) out += "Invalid: ${html(v.invalid.join(', '))}<br/>"
    if (v.nonLocal) out += "Non-local /24 ignored: ${html(v.nonLocal.join(', '))}<br/>"
    if (v.duplicate) out += "Duplicate ignored: ${html(v.duplicate.join(', '))}<br/>"
    out += "</div>"
    return out
}

String personIpReachabilityHtml(String ip) {
    Map status = (atomicState.ipStatus ?: [:]) as Map
    Map row = (status[ip] ?: [:]) as Map
    if (!row?.lastChecked) return "not checked yet"

    String result = (row.lastResult ?: "unknown").toString()
    String checked = row.lastChecked ? " at ${html(row.lastChecked.toString())}" : ""
    String error = row.error ? " (${html(row.error.toString())})" : ""
    if (row.lastReachable == true) return "<span style='color:green;font-weight:bold;'>reachable</span>${checked}"
    return "<span style='color:red;font-weight:bold;'>unreachable</span>${checked}${error}"
}

String personPresenceValidationHtml(Integer index) {
    def dev = settings[personPresenceInputKey(index)]
    if (!dev) return ""
    if (deviceHasPresenceAttribute(dev)) return "<span style='color:green;font-weight:bold;'>Presence attribute detected.</span>"
    return "<span style='color:red;font-weight:bold;'>Selected device does not expose a live presence attribute. Choose the Hubitat mobile app geolocation presence sensor.</span>"
}

Boolean visiblePresenceDeviceSelectionValid(Integer index) {
    def dev = settings[personPresenceInputKey(index)]
    if (!dev) return true
    return deviceHasPresenceAttribute(dev)
}

Boolean deviceHasPresenceAttribute(dev) {
    if (!dev) return false
    try { if (dev.hasAttribute("presence")) return true } catch (Throwable ignored) { }
    try {
        def attrs = dev.supportedAttributes
        if (attrs && attrs.any { a -> (a?.name ?: a?.toString())?.toString() == "presence" }) return true
    } catch (Throwable ignored) { }
    try {
        String value = dev.currentValue("presence")?.toString()
        if (value) return true
    } catch (Throwable ignored) { }
    return false
}

Boolean localSubnetRequired() {
    if (requireLocalIpSubnet == null) return true
    try { return requireLocalIpSubnet as Boolean } catch (Throwable ignored) { return true }
}

Boolean isValidIpv4(String ip) {
    if (!(ip ==~ /^\d{1,3}(\.\d{1,3}){3}$/)) return false
    List<Integer> parts = ip.split(/\./).collect { it as Integer }
    if (parts.any { it < 0 || it > 255 }) return false
    if (parts[0] == 0 || parts[0] == 127 || parts[0] >= 224) return false
    if (parts[3] == 0 || parts[3] == 255) return false
    return true
}

Boolean isLocalSlash24(String ip) {
    String hubIp = hubIpAddress()
    if (!hubIp || !isValidIpv4(hubIp)) return true
    List a = ip.split(/\./) as List
    List b = hubIp.split(/\./) as List
    return a[0] == b[0] && a[1] == b[1] && a[2] == b[2]
}

String hubIpAddress() {
    try {
        def hub = location?.hubs ? location.hubs[0] : null
        return (hub?.localIP ?: hub?.localIp ?: "").toString()
    } catch (Throwable ignored) {
        return ""
    }
}

// ---------------- Evidence freshness ----------------

void seedEvidenceTime(dev, String attr) {
    if (!dev || !attr) return
    String value = safeCurrentValue(dev, attr)
    if ((attr == "presence" && value == "present") || (attr == "switch" && value == "on")) {
        updateEvidenceTime(dev, attr)
    }
}

void updateEvidenceTime(dev, String attr) {
    if (!dev || !attr) return
    Map times = (atomicState.evidenceTimes ?: [:]) as Map
    times[evidenceKey(dev, attr)] = now()
    atomicState.evidenceTimes = times
}

Boolean isEvidenceStale(dev, String attr) {
    Long cutoffMs = staleEvidenceHoursValue() * 60L * 60L * 1000L
    if (cutoffMs <= 0L) return false
    Map times = (atomicState.evidenceTimes ?: [:]) as Map
    Long lastMs = (times[evidenceKey(dev, attr)] ?: 0L) as Long
    if (!lastMs) return false
    return (now() - lastMs) > cutoffMs
}

String evidenceKey(dev, String attr) {
    return "${dev?.id ?: dev?.displayName}:${attr}"
}

Boolean ipIsReachableAndFresh(Map row) {
    if (!row) return false
    Integer failures = (row.failureCount ?: 0) as Integer
    Long checkedMs = (row.lastCheckedMs ?: 0L) as Long
    Long successMs = (row.lastSuccessMs ?: 0L) as Long
    Long freshnessMs = Math.max(60000L, pingIntervalValue() * 2500L)

    if (row.lastReachable == true && checkedMs && (now() - checkedMs) <= freshnessMs) return true
    if (successMs && failures > 0 && failures < ipFailureThresholdValue()) return true
    return false
}

// ---------------- Ping parsing ----------------

Map parsePingOutcome(def pingData, String raw) {
    Integer rx = firstIntegerFromCandidates([
        propertyValue(pingData, "packetsReceived"),
        propertyValue(pingData, "received"),
        propertyValue(pingData, "rx"),
        regexInt(raw, /(?i)(?:packetsReceived|received|rx)\s*[=:]\s*(\d+)/),
        regexInt(raw, /(?i)(\d+)\s+(?:packets\s+)?received/)
    ])

    Integer tx = firstIntegerFromCandidates([
        propertyValue(pingData, "packetsTransmitted"),
        propertyValue(pingData, "transmitted"),
        propertyValue(pingData, "tx"),
        regexInt(raw, /(?i)(?:packetsTransmitted|transmitted|tx)\s*[=:]\s*(\d+)/),
        regexInt(raw, /(?i)(\d+)\s+(?:packets\s+)?transmitted/)
    ])

    Integer avg = firstIntegerFromCandidates([
        propertyValue(pingData, "averageMs"),
        propertyValue(pingData, "avg"),
        propertyValue(pingData, "average"),
        propertyValue(pingData, "time"),
        regexInt(raw, /(?i)(?:avg|average|time)\s*[=:]\s*(\d+)/)
    ])

    String loss = normalisePacketLoss(firstStringFromCandidates([
        propertyValue(pingData, "packetLoss"),
        propertyValue(pingData, "loss"),
        regexString(raw, /(?i)(\d+(?:\.\d+)?)\s*%\s*(?:packet\s*)?loss/),
        regexString(raw, /(?i)(?:packetLoss|loss)\s*[=:]\s*(\d+(?:\.\d+)?%?)/)
    ]))

    Boolean reachable = false
    Boolean confident = false

    if (rx != null) {
        reachable = rx > 0
        confident = true
    } else if (loss) {
        reachable = loss != "100%"
        confident = true
    } else {
        String s = (raw ?: "").toLowerCase()
        if (s.contains("reachable") || s.contains("success") || s.contains("alive")) {
            reachable = true
            confident = true
        } else if (s.contains("unreachable") || s.contains("timeout") || s.contains("100% packet loss")) {
            reachable = false
            confident = true
        }
    }

    return [
        reachable: reachable,
        confident: confident,
        packetsReceived: rx,
        packetsTransmitted: tx,
        packetLoss: loss,
        averageMs: avg
    ]
}

def propertyValue(obj, String name) {
    if (obj == null || !name) return null
    try { return obj."${name}" } catch (Throwable ignored) { }
    try { return obj.getAt(name) } catch (Throwable ignored) { }
    try {
        if (obj instanceof Map) return obj[name]
    } catch (Throwable ignored) { }
    return null
}

Integer regexInt(String raw, String pattern) {
    if (!raw) return null
    try {
        def m = raw =~ pattern
        if (m.find()) return Math.round(Float.parseFloat(m.group(1).toString())) as Integer
    } catch (Throwable ignored) { }
    return null
}

String regexString(String raw, String pattern) {
    if (!raw) return ""
    try {
        def m = raw =~ pattern
        if (m.find()) return m.group(1).toString()
    } catch (Throwable ignored) { }
    return ""
}

Integer firstIntegerFromCandidates(List candidates) {
    for (Object candidate : (candidates ?: [])) {
        Integer value = integerFromValue(candidate)
        if (value != null) return value
    }
    return null
}

String firstStringFromCandidates(List candidates) {
    for (Object candidate : (candidates ?: [])) {
        String value = candidate == null ? "" : candidate.toString().trim()
        if (value) return value
    }
    return ""
}

Integer integerFromValue(Object value) {
    if (value == null) return null
    String s = value.toString().trim()
    if (!s) return null
    s = s.replaceAll("[^0-9.]", "")
    if (!s) return null

    try {
        return Math.round(Float.parseFloat(s)) as Integer
    } catch (Throwable ignored) {
        return null
    }
}

String normalisePacketLoss(String value) {
    String s = (value ?: "").toString().trim()
    if (!s) return ""
    s = s.replaceAll("[^0-9.]", "")
    if (!s) return ""
    try {
        Integer rounded = Math.round(Float.parseFloat(s)) as Integer
        return "${Math.max(0, Math.min(100, rounded))}%"
    } catch (Throwable ignored) {
        return ""
    }
}

// ---------------- Settings helpers ----------------

Integer personCountValue() {
    try {
        Integer value = ((personCount ?: "1") as Integer)
        return Math.max(1, Math.min(maxPeople(), value))
    } catch (Throwable ignored) {
        return 1
    }
}

Boolean showAdvancedSettingsEnabled() {
    if (showAdvancedSettings == null) return false
    try { return showAdvancedSettings as Boolean } catch (Throwable ignored) { return false }
}

Integer pingIntervalValue() {
    try { return ((pingIntervalSeconds ?: "300") as Integer) } catch (Throwable ignored) { return 300 }
}

Integer pingCountValue() {
    try { return ((pingCount ?: "2") as Integer) } catch (Throwable ignored) { return 2 }
}

Integer evidenceWatchdogSecondsValue() {
    return 60
}

Integer ipFailureThresholdValue() {
    try {
        Integer value = ((ipFailureThreshold ?: 2) as Integer)
        return Math.max(1, value)
    } catch (Throwable ignored) {
        return 2
    }
}

Integer arrivalDelayValue() {
    try {
        Integer value = ((arrivalDelaySeconds ?: 0) as Integer)
        return Math.max(0, value)
    } catch (Throwable ignored) {
        return 0
    }
}

Integer departureDelaySecondsValue() {
    try {
        Integer minutes = ((departureDelayMinutes ?: 10) as Integer)
        return Math.max(0, minutes) * 60
    } catch (Throwable ignored) {
        return 600
    }
}

Object guestModeDurationHoursRawValue() {
    def raw = settings["guestModeDurationHours"]
    if (raw == null || raw.toString().trim() == "") raw = guestModeDurationHours
    if (raw == null || raw.toString().trim() == "") raw = 4
    return raw
}

Boolean guestModeDurationHoursIsValid() {
    try {
        BigDecimal value = new BigDecimal(guestModeDurationHoursRawValue().toString().trim())
        if (value.compareTo(BigDecimal.ONE) < 0) return false
        return value.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0
    } catch (Throwable ignored) {
        return false
    }
}

Integer guestModeDurationHoursValue() {
    try {
        if (!guestModeDurationHoursIsValid()) return 4
        BigDecimal value = new BigDecimal(guestModeDurationHoursRawValue().toString().trim())
        return Math.max(1, value.intValue())
    } catch (Throwable ignored) {
        return 4
    }
}

Long staleEvidenceHoursValue() {
    try {
        Integer hours = ((staleEvidenceHours ?: 12) as Integer)
        return Math.max(0, hours)
    } catch (Throwable ignored) {
        return 12L
    }
}

Integer personThreshold() { numericSetting("personPresentThreshold", 60, 0, 100) }
Integer houseThreshold() { numericSetting("housePresentThreshold", 60, 0, 100) }
Integer weightIp() { numericSetting("ipWeight", 70, 0, 100) }
Integer weightMobile() { numericSetting("mobilePresenceWeight", 60, 0, 100) }
Integer weightHouseSwitch() { numericSetting("houseSwitchWeight", 60, 0, 100) }

Integer numericSetting(String name, Integer defaultValue, Integer min, Integer max) {
    try {
        Integer value = (settings[name] ?: defaultValue) as Integer
        return Math.max(min, Math.min(max, value))
    } catch (Throwable ignored) {
        return defaultValue
    }
}

// ---------------- UI status ----------------

String mainUsersHtml() {
    List people = personProfiles()
    if (!people) return "No users have been saved yet. Open configuration to add the first user."

    String out = "<table style='border-collapse:collapse;width:100%;table-layout:fixed'>"
    out += headerRowFixed(["User", "Geolocation", "Configured IP", "IP status"], ["18%", "48%", "16%", "18%"])
    people.each { Map p ->
        def dev = (p.presenceDevices ?: []) ? (p.presenceDevices[0]) : null
        String devName = dev ? html(dev?.displayName ?: "selected") : "none configured"
        String presence = dev ? presenceStatusHtml(safeCurrentValue(dev, "presence") ?: "unknown") : ""
        String ip = (p.ips ?: []) ? (p.ips[0] ?: "") : ""
        String ipStatus = ip ? ipStatusSummaryHtml(ip, ((atomicState.ipStatus ?: [:]) as Map)[ip] as Map) : "none configured"
        out += dataRowFixed([
            html(p.name ?: ""),
            dev ? "${devName} = ${presence}" : devName,
            html(ip ?: "none configured"),
            ipStatus
        ], ["18%", "48%", "16%", "18%"])
    }
    out += "</table>"
    return out
}


String applicationChildSwitchSummaryHtml() {
    def main = null
    try { main = outputDevice() } catch (Throwable ignored) { }
    def gm = existingGuestModeSwitchDevice()
    String mode = (outputMode ?: "child").toString()
    String modeLabel = "Managed child switch"
    if (mode == "existing") modeLabel = "Assigned existing switch"
    if (mode == "presence") modeLabel = "Assigned existing writable presence / aggregator device"

    String mainName = main ? (main.displayName ?: configuredChildName()) : "not configured"
    String guestName = gm ? (gm.displayName ?: configuredGuestModeChildName()) : "not yet created"

    String mainState = main ? (safeCurrentValue(main, "switch") ?: safeCurrentValue(main, "presence") ?: "unknown") : "not configured"
    String guestState = gm ? (safeCurrentValue(gm, "switch") ?: "unknown") : "not configured"

    String out = "<table style='border-collapse:collapse;width:100%;table-layout:fixed'>"
    out += headerRowFixed(["Switch", "Current name", "Current state / assignment"], ["24%", "38%", "38%"])
    out += dataRowFixed(["Main status target", html(mainName), "${html(mainState)}<br/><span style='color:#777'>${html(modeLabel)}</span>"], ["24%", "38%", "38%"])
    out += dataRowFixed(["Guest Mode child switch", html(guestName), "${html(guestState)}<br/><span style='color:#777'>Managed child switch</span>"], ["24%", "38%", "38%"])
    out += "</table>"
    return out
}

String guestModeMainTimerHtml() {
    Integer hours = guestModeDurationHoursValue()
    Long until = (atomicState.guestModeUntil ?: 0L) as Long
    String durationText = "${hours} hour${hours == 1 ? '' : 's'}"
    String timerText = ""
    if (until && until > now()) {
        timerText = "active - ends at ${formatEpochMs(until)} (${remainingTimeText(until)} remaining)"
    } else {
        timerText = "not active - if started now, it will end at ${formatEpochMs(now() + (hours * 60L * 60L * 1000L))}"
    }

    String out = responsiveUiCssHtml()
    out += "<div class='pm-desktop'>"
    out += "<table class='pm-table'>"
    out += rowHtml("Selected duration", durationText)
    out += rowHtml("Timer", timerText)
    out += "</table>"
    out += "</div>"

    out += "<div class='pm-mobile'>"
    out += "<div class='pm-card'>"
    out += mobileFieldHtml("Selected duration", html(durationText))
    out += mobileFieldHtml("Timer", html(timerText))
    out += "</div>"
    out += "</div>"
    return out
}

String guestModeTimerStatusText() {
    Long until = (atomicState.guestModeUntil ?: 0L) as Long
    if (until && until > now()) return "active - ends at ${formatEpochMs(until)} (${remainingTimeText(until)} remaining)"
    return "not active"
}

String remainingTimeText(Long untilMs) {
    Long remainingMs = Math.max(0L, (untilMs ?: 0L) - now())
    Long totalMinutes = Math.round(remainingMs / 60000.0) as Long
    Long hours = Math.floor(totalMinutes / 60.0) as Long
    Long minutes = totalMinutes % 60L
    if (hours > 0L) return "${hours}h ${minutes}m"
    return "${minutes}m"
}

String statusHtml() {
    processExpiredPendingActionIfDue("status page refresh")
    Boolean manualSnapshot = manualDashboardSnapshotActive()
    Map result = evaluateAllEvidence(manualSnapshot)
    if (!manualSnapshot) scheduleEvaluationIfSummaryOutOfSync(result)
    List peopleForStatus = result.people ?: []
    List houseDevices = asList(houseEvidenceSwitches)

    String out = ""
    out += responsiveUiCssHtml()
    out += "<div class='pm-overall'><div class='pm-overall-label'>Overall Status</div><div>${overallStatusHtml(result)}</div></div><br/>"

    out += "<b>Occupation Telemetry</b><br/>"
    out += statusTelemetryDesktopHtml(peopleForStatus, houseDevices, manualSnapshot)
    out += statusTelemetryMobileHtml(peopleForStatus, houseDevices, manualSnapshot)
    return out
}

String statusTelemetryDesktopHtml(List peopleForStatus, List houseDevices, Boolean manualSingleSweepIp = false) {
    String out = "<div class='pm-desktop'><div class='pm-table-wrap'>"
    out += "<table class='pm-table'>"
    out += headerRowFixed(["User / System", "Effective Status", "Hubitat / 3rd Party Presence", "LAN IP"], ["22%", "16%", "28%", "34%"])

    if (!peopleForStatus && !houseDevices) {
        out += dataRowFixed(["<span style='color:#777;font-weight:bold;'>Pending</span>", "", "", ""], ["22%", "16%", "28%", "34%"])
    }

    peopleForStatus.each { Map p ->
        def dev = (p.presenceDevices ?: []) ? (p.presenceDevices[0]) : null
        String effective = effectivePersonStatusHtml(p)
        String geo = dev ? homeDepartedFromPresenceHtml(safeCurrentValue(dev, "presence") ?: "unknown") : ""
        String ip = (p.ips ?: []) ? (p.ips[0] ?: "").toString() : ""
        String ipCell = ip ? "${html(ip)} - ${lanIpPresenceHtml(ip, ((atomicState.ipStatus ?: [:]) as Map)[ip] as Map, manualSingleSweepIp)}" : ""
        out += dataRowFixed([html(p.name ?: ""), effective, geo, ipCell], ["22%", "16%", "28%", "34%"])
    }

    houseDevices.eachWithIndex { dev, Integer idx ->
        String value = safeCurrentValue(dev, "switch") ?: "unknown"
        String systemName = dev?.displayName ?: "3rd Party ${idx + 1}"
        String serviceStatus = homeDepartedFromSwitchHtml(value)
        out += dataRowFixed([html(systemName), serviceStatus, serviceStatus, ""], ["22%", "16%", "28%", "34%"])
    }

    out += "</table></div></div>"
    return out
}

String statusTelemetryMobileHtml(List peopleForStatus, List houseDevices, Boolean manualSingleSweepIp = false) {
    String out = "<div class='pm-mobile'>"

    if (!peopleForStatus && !houseDevices) {
        out += "<div class='pm-card'><div class='pm-card-title'>Pending</div><div class='pm-muted'>No users or Third Party Services are configured yet.</div></div>"
    }

    peopleForStatus.each { Map p ->
        def dev = (p.presenceDevices ?: []) ? (p.presenceDevices[0]) : null
        String effective = effectivePersonStatusHtml(p)
        String geo = dev ? homeDepartedFromPresenceHtml(safeCurrentValue(dev, "presence") ?: "unknown") : "<span class='pm-muted'>none configured</span>"
        String ip = (p.ips ?: []) ? (p.ips[0] ?: "").toString() : ""
        String ipCell = ip ? "${html(ip)} - ${lanIpPresenceHtml(ip, ((atomicState.ipStatus ?: [:]) as Map)[ip] as Map, manualSingleSweepIp)}" : "<span class='pm-muted'>none configured</span>"

        out += "<div class='pm-card'>"
        out += "<div class='pm-card-title'>${html(p.name ?: '')}</div>"
        out += mobileFieldHtml("Effective Status", effective)
        out += mobileFieldHtml("Hubitat / 3rd Party Presence", geo)
        out += mobileFieldHtml("LAN IP", ipCell)
        out += "</div>"
    }

    houseDevices.eachWithIndex { dev, Integer idx ->
        String value = safeCurrentValue(dev, "switch") ?: "unknown"
        String systemName = dev?.displayName ?: "3rd Party ${idx + 1}"
        String serviceStatus = homeDepartedFromSwitchHtml(value)
        out += "<div class='pm-card'>"
        out += "<div class='pm-card-title'>${html(systemName)}</div>"
        out += mobileFieldHtml("Effective Status", serviceStatus)
        out += mobileFieldHtml("Third Party Presence", serviceStatus)
        out += mobileFieldHtml("LAN IP", "<span class='pm-muted'>not applicable</span>")
        out += "</div>"
    }

    out += "</div>"
    return out
}

String overallStatusHtml(Map result) {
    String override = manualOverrideValue()
    if (override == "occupied") return "<span style='color:green;font-weight:bold;'>Home (FORCED)</span>"
    if (override == "empty") return "<span style='color:red;font-weight:bold;'>Away (FORCED)</span>"

    Long until = (atomicState.guestModeUntil ?: 0L) as Long
    if (guestModeActive()) {
        if (until && until > now()) return "<span style='color:#1a5cff;font-weight:bold;'>Guest Mode until ${html(formatEpochMsShort(until))}</span>"
        return "<span style='color:#1a5cff;font-weight:bold;'>Guest Mode active</span>"
    }

    if (result?.hasConfiguredPresenceSources != true) {
        return "<span style='color:#777;font-weight:bold;'>Pending user and 3rd party systems configuration</span>"
    }

    String pending = (atomicState.pendingAction ?: "").toString().toLowerCase()
    String value = summaryOccupancyValue(result)
    if (value == "occupied") {
        if (pending == "empty") return "<span style='color:green;font-weight:bold;'>Home</span> <span style='color:#777;font-weight:bold;'>(Away pending - ${html(pendingDelayRemainingText('departure'))})</span>"
        return "<span style='color:green;font-weight:bold;'>Home</span>"
    }
    if (value == "empty") {
        if (pending == "occupied") return "<span style='color:red;font-weight:bold;'>Away</span> <span style='color:#777;font-weight:bold;'>(Home pending - ${html(pendingDelayRemainingText('arrival'))})</span>"
        return "<span style='color:red;font-weight:bold;'>Away</span>"
    }
    if (pending == "occupied") return "<span style='color:#777;font-weight:bold;'>Home pending - ${html(pendingDelayRemainingText('arrival'))}</span>"
    if (pending == "empty") return "<span style='color:#777;font-weight:bold;'>Away pending - ${html(pendingDelayRemainingText('departure'))}</span>"
    return "<span style='color:#777;font-weight:bold;'>Unknown</span>"
}

String effectivePersonStatusHtml(Map p) {
    Integer score = 0
    try { score = (p?.score ?: 0) as Integer } catch (Throwable ignored) { score = 0 }
    if (p?.present == true) return "<span style='color:green;font-weight:bold;'>Home</span> <span style='color:#777'>(${score}%)</span>"
    return "<span style='color:red;font-weight:bold;'>Departed</span> <span style='color:#777'>(${score}%)</span>"
}

String pendingDelayRemainingText(String delayLabel) {
    Long dueMs = pendingDueMsValue()
    if (dueMs <= 0L) return "--:-- ${delayLabel} delay remaining"

    Long remainingMs = dueMs - now()
    if (remainingMs < 0L) remainingMs = 0L

    Long remainingSeconds = Math.ceil((remainingMs as Double) / 1000D) as Long
    return "${formatDurationMmSs(remainingSeconds)} ${delayLabel} delay remaining"
}

Long pendingDueMsValue() {
    try { return ((atomicState.pendingDueMs ?: 0L) as Long) } catch (Throwable ignored) { return 0L }
}

String formatDurationMmSs(Long totalSeconds) {
    if (totalSeconds == null || totalSeconds < 0L) totalSeconds = 0L
    Long minutes = totalSeconds.intdiv(60) as Long
    Long seconds = totalSeconds % 60L
    return "${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}"
}

String homeDepartedFromPresenceHtml(String value) {
    String normalised = (value ?: "unknown").toString().toLowerCase()
    if (normalised == "present") return "<span style='color:green;font-weight:bold;'>Home</span>"
    if (normalised == "not present" || normalised == "not_present" || normalised == "departed") return "<span style='color:red;font-weight:bold;'>Departed</span>"
    return html(value ?: "unknown")
}

String homeDepartedFromSwitchHtml(String value) {
    String normalised = (value ?: "unknown").toString().toLowerCase()
    if (normalised == "on") return "<span style='color:green;font-weight:bold;'>Home</span>"
    if (normalised == "off") return "<span style='color:red;font-weight:bold;'>Departed</span>"
    return html(value ?: "unknown")
}

String lanIpPresenceHtml(String ip, Map row, Boolean manualSingleSweepIp = false) {
    // On the 30-second post-Refresh-data screen window, the LAN IP column must
    // show the exact result from the sweep that just completed. It must not
    // carry forward a previous success under the normal failure threshold.
    if (manualSingleSweepIp == true) {
        if (row?.lastReachable == true) return "<span style='color:green;font-weight:bold;'>present</span>"
        if ((row?.lastResult ?: "").toString() == "unreachable") return "<span style='color:red;font-weight:bold;'>unreachable</span>"
        return html((row?.lastResult ?: "not checked").toString())
    }

    if (ipIsReachableAndFresh(row)) return "<span style='color:green;font-weight:bold;'>present</span>"
    String resultText = (row?.lastResult ?: "not checked").toString()
    if (resultText == "unreachable") return "<span style='color:red;font-weight:bold;'>unreachable</span>"
    return html(resultText)
}

void recordUserStatusChanges(Map result) {
    Map previous = (atomicState.personStatusSnapshot ?: [:]) as Map
    Map next = [:]
    Long nowMs = now()
    Integer departureHoldSeconds = userDepartureReportHoldSeconds()

    (result?.people ?: []).each { Map p ->
        String key = "person${p.index}"
        String name = (p.name ?: "Person ${p.index}").toString()
        String liveStatus = p.present == true ? "Home" : "Departed"
        Integer score = (p.score ?: 0) as Integer

        Map prior = [:]
        def priorRaw = previous[key]
        if (priorRaw instanceof Map) prior = priorRaw as Map
        else if (priorRaw) prior = [reportedStatus: priorRaw.toString(), status: priorRaw.toString()]

        String reportedStatus = (prior.reportedStatus ?: prior.status ?: "").toString()
        String candidateStatus = (prior.candidateStatus ?: "").toString()
        Long candidateSinceMs = longValue(prior.candidateSinceMs, 0L)

        if (!reportedStatus) {
            reportedStatus = liveStatus
            addHistory("user status", "${name}: ${reportedStatus}", key)
            candidateStatus = ""
            candidateSinceMs = 0L
        } else if (reportedStatus != liveStatus) {
            if (liveStatus == "Home") {
                reportedStatus = "Home"
                candidateStatus = ""
                candidateSinceMs = 0L
                addHistory("user status", "${name}: Home", key)
            } else {
                if (candidateStatus != "Departed") {
                    candidateStatus = "Departed"
                    candidateSinceMs = nowMs
                }

                Long dueMs = candidateSinceMs + (departureHoldSeconds * 1000L)
                if (nowMs >= dueMs) {
                    reportedStatus = "Departed"
                    candidateStatus = ""
                    candidateSinceMs = 0L
                    addHistory("user status", "${name}: Departed", key)
                }
            }
        } else {
            candidateStatus = ""
            candidateSinceMs = 0L
        }

        next[key] = [
            name: name,
            status: reportedStatus,
            reportedStatus: reportedStatus,
            liveStatus: liveStatus,
            candidateStatus: candidateStatus,
            candidateSinceMs: candidateSinceMs,
            score: score
        ]
    }

    atomicState.personStatusSnapshot = next
}

Integer userDepartureReportHoldSeconds() {
    Integer pingPlusBuffer = pingIntervalValue() + 30
    Integer departureDelay = departureDelaySecondsValue()
    Integer hold = departureDelay > 0 ? Math.min(departureDelay, pingPlusBuffer) : pingPlusBuffer
    return Math.max(60, Math.min(600, hold))
}

Long longValue(Object value, Long fallback = 0L) {
    if (value == null) return fallback
    try { return value as Long } catch (Throwable ignored) { }
    try { return Long.parseLong(value.toString()) } catch (Throwable ignored) { }
    return fallback
}

void recordThirdPartyStatusChanges(Map result) {
    Map previous = (atomicState.thirdPartyStatusSnapshot ?: [:]) as Map
    Map next = [:]

    ((result?.household?.providers ?: []) as List).each { Map provider ->
        String key = (provider.key ?: provider.name ?: "thirdParty").toString()
        String name = (provider.name ?: "Third Party Provider").toString()
        String status = (provider.status ?: "Departed").toString()
        String priorStatus = ""

        def prior = previous[key]
        if (prior instanceof Map) priorStatus = (prior.status ?: "").toString()
        else if (prior) priorStatus = prior.toString()

        next[key] = [name: name, status: status]

        if (!priorStatus || priorStatus != status) {
            addHistory("third party status", "${name}: ${status}", key)
        }
    }

    atomicState.thirdPartyStatusSnapshot = next
}

String deviceHistoryKey(dev) {
    if (!dev) return "unknown"
    try { return dev.id?.toString() ?: dev.displayName?.toString() ?: "unknown" } catch (Throwable ignored) { return dev.displayName?.toString() ?: "unknown" }
}

String occupancyReportLabel(String value) {
    String normalised = (value ?: "unknown").toString().toLowerCase()
    if (normalised == "occupied") return "Home"
    if (normalised == "empty") return "Away"
    return "Unknown"
}

String activityReportHtml() {
    List history = reportableHistoryRows((atomicState.history ?: []) as List)
    if (!history) return responsiveUiCssHtml() + "No report events recorded yet."

    String out = responsiveUiCssHtml()
    out += "<div class='pm-desktop'><div class='pm-table-wrap'>"
    out += "<table class='pm-table'>"
    out += headerRowFixed(["Time", "Event", "Detail"], ["22%", "18%", "60%"])
    history.take(historyLimitValue()).each { Map row ->
        out += dataRowFixed([html(row.time ?: ""), historyTypeLabel(row.type), html(row.message ?: "")], ["22%", "18%", "60%"])
    }
    out += "</table></div></div>"

    out += "<div class='pm-mobile'>"
    history.take(historyLimitValue()).each { Map row ->
        out += "<div class='pm-event-card'>"
        out += "<div class='pm-event-time'>${html(row.time ?: '')}</div>"
        out += "<div class='pm-event-type'>${historyTypeLabel(row.type)}</div>"
        out += "<div class='pm-value'>${html(row.message ?: '')}</div>"
        out += "</div>"
    }
    out += "</div>"
    return out
}

// ---------------- Presence report ----------------

Integer presenceReportDayCount() { return 30 }

Long presenceReportDayStartMs(Integer daysAgo) {
    Calendar cal = Calendar.getInstance(location.timeZone)
    cal.setTimeInMillis(now())
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.DAY_OF_MONTH, -daysAgo)
    return cal.getTimeInMillis()
}

String presenceReportDayLabel(Long dayStartMsValue) {
    return new Date(dayStartMsValue).format("yyyy-MM-dd", location.timeZone)
}

// Reconstructs a person's Home intervals from their "user status" Activity Report
// rows (added by recordUserStatusChanges, keyed by dedupeKey "person<index>"). Rows
// from before a person was deleted and their slot reused by someone else keep the
// same "person<index>" dedupeKey, so a report window spanning that deletion can
// briefly attribute the deleted person's old hours to whoever now occupies that
// slot. Narrow, known limitation - not fixed here, same root cause as the
// personStatusSnapshot reindex fixed in 4.1, just not retroactive to old rows.
List<Map> personHomeIntervalsMs(String dedupeKey) {
    List rows = ((atomicState.history ?: []) as List).findAll { Map row ->
        (row.type ?: "") == "user status" && (row.dedupeKey ?: "") == dedupeKey && row.timeMs != null
    }
    rows = rows.sort { (it.timeMs ?: 0L) as Long }

    List<Map> intervals = []
    Long openStart = null
    rows.each { Map row ->
        Boolean isHome = (row.message ?: "").toString().endsWith(": Home")
        Long eventMs = (row.timeMs ?: 0L) as Long
        if (isHome) {
            if (openStart == null) openStart = eventMs
        } else if (openStart != null) {
            intervals << [start: openStart, end: eventMs]
            openStart = null
        }
    }
    if (openStart != null) intervals << [start: openStart, end: now()]
    return intervals
}

Long overlapMs(List<Map> intervals, Long windowStart, Long windowEnd) {
    Long total = 0L
    intervals.each { Map iv ->
        Long start = Math.max(iv.start as Long, windowStart)
        Long end = Math.min(iv.end as Long, windowEnd)
        if (end > start) total += (end - start)
    }
    return total
}

String formatHoursMinutes(Long totalMs) {
    Long safeMs = (totalMs == null || totalMs < 0L) ? 0L : totalMs
    Long totalMinutes = Math.round(safeMs / 60000.0) as Long
    Long hours = totalMinutes.intdiv(60) as Long
    Long minutes = totalMinutes % 60L
    return "${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}"
}

String presenceReportHtml() {
    List<Map> people = personProfiles()
    if (!people) return responsiveUiCssHtml() + "No users configured yet."

    Integer dayCount = presenceReportDayCount()
    Long nowMs = now()

    Map<String, List<Map>> intervalsByKey = [:]
    Map<String, Long> totalsByKey = [:]
    people.each { Map p ->
        String key = "person${p.index}"
        intervalsByKey[key] = personHomeIntervalsMs(key)
        totalsByKey[key] = 0L
    }

    List<Map> dayRows = []
    for (int daysAgo = 0; daysAgo < dayCount; daysAgo++) {
        Long dayStart = presenceReportDayStartMs(daysAgo)
        Long dayEnd = (daysAgo == 0) ? nowMs : (dayStart + (24L * 60L * 60L * 1000L))
        Map<String, String> values = [:]
        people.each { Map p ->
            String key = "person${p.index}"
            Long ms = overlapMs(intervalsByKey[key], dayStart, dayEnd)
            totalsByKey[key] = (totalsByKey[key] ?: 0L) + ms
            values[key] = formatHoursMinutes(ms)
        }
        String label = presenceReportDayLabel(dayStart) + (daysAgo == 0 ? " (today, so far)" : "")
        dayRows << [label: label, values: values]
    }

    String out = responsiveUiCssHtml()
    out += "<div class='pm-table-wrap'>"
    out += "<table class='pm-scroll-table'>"
    out += "<tr><th class='pm-date-col'>Date</th>" + people.collect { Map p -> "<th class='pm-hours-col'>${html(p.name ?: '')}</th>" }.join("") + "</tr>"
    dayRows.each { Map row ->
        out += "<tr><td class='pm-date-col'>${html(row.label)}</td>" + people.collect { Map p -> "<td class='pm-hours-col'>${html((row.values as Map)["person${p.index}"] ?: '00:00')}</td>" }.join("") + "</tr>"
    }
    out += "<tr class='pm-total-row'><td class='pm-date-col'>Total (${dayCount} days)</td>" + people.collect { Map p -> "<td class='pm-hours-col'>${html(formatHoursMinutes(totalsByKey["person${p.index}"] ?: 0L))}</td>" }.join("") + "</tr>"
    out += "</table>"
    out += "</div>"
    return out
}

String decisionDetailHtml() {
    Map result = evaluateAllEvidence()
    Map ipMap = (atomicState.ipStatus ?: [:]) as Map
    def output = null
    try { output = outputDevice() } catch (Throwable ignored) { }
    def gm = existingGuestModeSwitchDevice()

    String out = ""
    out += "<b>Decision detail</b><br/>"
    out += "<table style='border-collapse:collapse;width:100%'>"
    out += rowHtml("Current occupancy", atomicState.currentOccupancy ?: "unknown")
    out += rowHtml("Last committed occupancy", committedOccupancyState())
    out += rowHtml("Confidence", "${atomicState.currentConfidence ?: 0}%")
    out += rowHtml("Main status target", output ? output.displayName : "not configured")
    out += rowHtml("Manual override", manualOverrideStatusText())
    out += rowHtml("Guest Mode", guestModeActive() ? "active" : "inactive")
    out += rowHtml("Guest Mode switch", gm ? "${gm.displayName} = ${safeCurrentValue(gm, 'switch') ?: 'unknown'}" : "not configured")
    out += rowHtml("Guest Mode timer", atomicState.guestModeUntilText ?: "not active")
    out += rowHtml("Pending action", atomicState.pendingAction ?: "none")
    out += rowHtml("Pending schedule", atomicState.pendingScheduleStatus ?: "")
    out += rowHtml("Presence subscriptions", atomicState.presenceSubscriptionSummary ?: "none")
    out += rowHtml("Switch subscriptions", atomicState.switchSubscriptionSummary ?: "none")
    out += rowHtml("Last evidence event", atomicState.lastEvidenceEvent ?: "none")
    out += rowHtml("Last subscription refresh", atomicState.lastSubscriptionRefresh ?: "")
    out += rowHtml("Last decision", atomicState.lastDecision ?: "")
    out += rowHtml("Last changed", atomicState.lastChanged ?: "")
    out += rowHtml("Last evaluation", atomicState.lastEvaluation ?: "")
    out += rowHtml("Notification devices", notificationDeviceSummaryText())
    out += rowHtml("Last notification", atomicState.lastNotificationText ? "${atomicState.lastNotificationText} at ${atomicState.lastNotificationTime ?: ''}" : "none")
    out += rowHtml("Last notification attempt", atomicState.lastNotificationAttemptText ? "${atomicState.lastNotificationAttemptText} at ${atomicState.lastNotificationAttemptTime ?: ''}; devices=${atomicState.lastNotificationAttemptDevices ?: 0}; accepted=${atomicState.lastNotificationAcceptedCount ?: 0}${atomicState.lastNotificationFailure ? '; error=' + atomicState.lastNotificationFailure : ''}" : "none")
    out += rowHtml("Evidence summary", result.summary ?: "")
    out += "</table><br/>"

    out += "<b>People, phone presence and IP decision inputs</b><br/>"
    out += "<table style='border-collapse:collapse;width:100%'>"
    out += headerRow(["Person", "Hubitat mobile app geolocation presence sensor", "Configured IP", "Score", "Present", "Evidence", "Ignored"])
    (result.people ?: []).each { Map p ->
        List mobileNames = (p.presenceDevices ?: []).collect { html(it?.displayName ?: "") }.findAll { it }
        List ipNames = (p.ips ?: []).collect { html(it ?: "") }.findAll { it }
        out += dataRow([
            html(p.name ?: ""),
            mobileNames ? mobileNames.join("<br/>") : "none configured",
            ipNames ? ipNames.join("<br/>") : "none configured",
            "${p.score ?: 0}%",
            p.present ? "yes" : "no",
            (p.evidence ?: []).collect { html(it) }.join("<br/>") ?: "none",
            (p.ignored ?: []).collect { html(it) }.join("<br/>") ?: "none"
        ])
    }
    out += "</table><br/>"

    out += "<b>Third Party Services</b><br/>"
    out += "<table style='border-collapse:collapse;width:100%'>"
    out += headerRow(["Device", "Status", "Stale", "Evidence"])
    List houseDevices = asList(houseEvidenceSwitches)
    if (houseDevices) {
        houseDevices.each { dev ->
            String value = safeCurrentValue(dev, "switch") ?: "unknown"
            Boolean stale = isEvidenceStale(dev, "switch")
            out += dataRow([
                html(dev?.displayName ?: ""),
                thirdPartyServiceStatusHtml(value),
                stale ? "yes" : "no",
                (value == "on" && !stale) ? "counts as Third Party Services" : "not currently counted"
            ])
        }
    } else {
        out += dataRow(["none configured", "", "", ""])
    }
    out += "</table><br/>"

    out += "<b>IP status</b><br/>"
    out += "<table style='border-collapse:collapse;width:100%'>"
    out += headerRow(["IP", "Result", "Failures", "Rx/Tx", "Loss", "Avg ms", "Last checked", "Error"])
    if (ipMap) {
        ipMap.keySet().sort().each { String ip ->
            Map row = ipMap[ip] as Map
            out += dataRow([
                html(ip),
                html(row?.lastResult ?: "not checked"),
                row?.failureCount ?: 0,
                "${row?.packetsReceived ?: ''}/${row?.packetsTransmitted ?: ''}",
                html(row?.packetLoss ?: ""),
                row?.averageMs ?: "",
                html(row?.lastChecked ?: ""),
                html(row?.error ?: "")
            ])
        }
    } else {
        out += dataRow(["none", "No IP addresses configured", "", "", "", "", "", ""])
    }
    out += "</table>"

    return out
}

String summaryOccupancyValue(Map result) {
    String override = manualOverrideValue()
    if (override == "occupied") return "occupied"
    if (override == "empty") return "empty"
    if (result?.hasConfiguredPresenceSources != true && result?.guestModeActive != true) return "pending_config"
    return committedOccupancyState()
}

String committedOccupancyState() {
    String committed = (atomicState.lastOverallCommittedState ?: "").toString().toLowerCase()
    if (committed in ["occupied", "empty"]) return committed

    String current = (atomicState.currentOccupancy ?: "unknown").toString().toLowerCase()
    if (current in ["occupied", "empty"]) return current
    return "unknown"
}

void scheduleEvaluationIfSummaryOutOfSync(Map result) {
    if (manualOverrideActive()) return
    if (result?.hasConfiguredPresenceSources != true && result?.guestModeActive != true) return
    String desired = ""
    if (result?.occupied == true) desired = "occupied"
    else if (result?.allAbsent == true) desired = "empty"
    if (!desired) return

    String current = (atomicState.currentOccupancy ?: "unknown").toString().toLowerCase()
    if (current != desired) {
        atomicState.lastDecision = "Evaluation queued from status summary - live evidence indicates ${desired}"
        runIn(1, "evaluateFromSchedule", [overwrite: true])
    }
}

String occupancyStatusHtml(String value) {
    String normalised = (value ?: "unknown").toString().toLowerCase()
    if (normalised == "occupied") return "<span style='color:green;font-weight:bold;'>OCCUPIED</span>"
    if (normalised == "empty") return "<span style='color:red;font-weight:bold;'>NOT OCCUPIED</span>"
    return "<span style='color:#777;font-weight:bold;'>UNKNOWN</span>"
}

String guestModeSwitchStatusHtml(dev) {
    String value = safeCurrentValue(dev, "switch") ?: "unknown"
    String text = "${dev?.displayName ?: 'Guest Mode'} = ${value}"
    if (value == "on") return "<span style='color:green;font-weight:bold;'>${html(text)}</span>"
    return html(text)
}

String thirdPartyServiceStatusHtml(String value) {
    String normalised = (value ?: "unknown").toString().toLowerCase()
    if (normalised == "on") return "<span style='color:green;font-weight:bold;'>Present</span>"
    if (normalised == "off") return "<span style='color:red;font-weight:bold;'>Not Present</span>"
    return html(value ?: "unknown")
}

String presenceStatusHtml(String value) {
    String normalised = (value ?: "unknown").toString().toLowerCase()
    if (normalised == "present") return "<span style='color:green;font-weight:bold;'>present</span>"
    if (normalised == "not present") return "<span style='color:red;font-weight:bold;'>not present</span>"
    return html(value ?: "unknown")
}

String ipStatusSummaryHtml(String ip, Map row) {
    String resultText = (row?.lastResult ?: "not checked").toString()
    if (row?.lastReachable == true) return "<span style='color:green;font-weight:bold;'>reachable</span>"
    if (resultText == "unreachable") return "<span style='color:red;font-weight:bold;'>unreachable</span>"
    return html(resultText)
}

String mobileFieldHtml(String label, String rawValue) {
    return "<div class='pm-field'><div class='pm-label'>${html(label)}</div><div class='pm-value'>${rawValue ?: ''}</div></div>"
}

String rowHtmlRawValue(String k, String rawValue) {
    return "<tr><td style='border:1px solid #ddd;padding:4px;width:230px'><b>${html(k)}</b></td><td style='border:1px solid #ddd;padding:4px'>${rawValue ?: ''}</td></tr>"
}

String rowHtml(String k, String v) {
    return "<tr><td style='border:1px solid #ddd;padding:4px;width:230px'><b>${html(k)}</b></td><td style='border:1px solid #ddd;padding:4px'>${html(v)}</td></tr>"
}

String headerRow(List cols) {
    return headerRowFixed(cols, [])
}

String dataRow(List cols) {
    return dataRowFixed(cols, [])
}

String headerRowFixed(List cols, List widths) {
    String out = "<tr>"
    for (int i = 0; i < cols.size(); i++) {
        String widthStyle = (widths && widths.size() > i && widths[i]) ? ";width:${widths[i]}" : ""
        out += "<th style='border:1px solid #ddd;padding:4px;text-align:left;vertical-align:top${widthStyle}'>${html(cols[i])}</th>"
    }
    out += "</tr>"
    return out
}

String dataRowFixed(List cols, List widths) {
    String out = "<tr>"
    for (int i = 0; i < cols.size(); i++) {
        String widthStyle = (widths && widths.size() > i && widths[i]) ? ";width:${widths[i]}" : ""
        out += "<td style='border:1px solid #ddd;padding:4px;vertical-align:top${widthStyle}'>${cols[i] == null ? '' : cols[i]}</td>"
    }
    out += "</tr>"
    return out
}

// dedupeKey identifies the reporting entity (e.g. "person2", a third-party device
// key) behind a row. Two different entities that happen to render identical
// type+message text (for example two people sharing a display name) must not
// suppress each other's real Home/Departed transition, so the adjacent-duplicate
// check below also requires the dedupeKey to match. Callers that do not pass one
// keep the original text-only behaviour.
void addHistory(String type, String message, String dedupeKey = null) {
    String safeType = safeMessage(type)
    String safeMsg = safeMessage(message)
    if (!isReportableHistoryRow([type: safeType, message: safeMsg])) return

    String key = (dedupeKey?.toString()?.trim()) ? safeMessage(dedupeKey.toString()) : safeMsg

    List history = (atomicState.history ?: []) as List
    Map latest = history ? (history[0] as Map) : [:]
    String latestKey = (latest.containsKey("dedupeKey") ? latest.dedupeKey : latest.message ?: "").toString()
    if ((latest.type ?: "") == safeType && (latest.message ?: "") == safeMsg && latestKey == key) return

    history.add(0, [time: timestamp(), timeMs: now(), type: safeType, message: safeMsg, dedupeKey: key])
    atomicState.history = history.take(historyLimitValue())
}

List reportableHistoryRows(List history) {
    List rows = ((history ?: []) as List).findAll { row -> isReportableHistoryRow(row as Map) }
    List clean = []
    rows.each { Map row ->
        Map prior = clean ? (clean[-1] as Map) : [:]
        String rowKey = "${row.type}|${row.message}"
        String priorKey = prior ? "${prior.type}|${prior.message}" : ""
        if (rowKey != priorKey) clean << row
    }
    return clean
}

Boolean isReportableHistoryRow(Map row) {
    String type = (row?.type ?: "").toString().toLowerCase()
    String message = (row?.message ?: "").toString()

    if (type == "initialised") return message == "Presence Manager initialised"
    if (type == "person") {
        return message.startsWith("User created:") ||
               message.startsWith("User edited:") ||
               message.startsWith("User deleted:")
    }
    if (type == "user status") return message.endsWith(": Home") || message.endsWith(": Departed")
    if (type == "home status") return message == "Overall home status: Home" ||
                                      message == "Overall home status: Departed" ||
                                      message.startsWith("Overall away blocked:")
    if (type == "manual override") {
        return message.startsWith("Force Home override") ||
               message.startsWith("Force Away override") ||
               message.startsWith("Force override removed") ||
               message.startsWith("Remove Force pressed")
    }
    if (type == "third party status") return message.endsWith(": Home") || message.endsWith(": Departed")
    if (type == "guest mode") {
        return message.startsWith("Guest Mode started") ||
               message.startsWith("Guest Mode ended")
    }
    return false
}

String historyTypeLabel(String type) {
    String normalised = (type ?: "").toString().toLowerCase()
    if (normalised == "initialised") return "System"
    if (normalised == "person") return "User Config"
    if (normalised == "user status") return "User Status"
    if (normalised == "manual override") return "Manual Override"
    if (normalised == "home status") return "Home Status"
    if (normalised == "third party status") return "Third Party"
    if (normalised == "guest mode") return "Guest Mode"
    return html(type ?: "")
}

void clearHistory() {
    atomicState.history = []
}

Integer historyLimitValue() {
    try { return Math.max(5, Math.min(500, (historyLimit ?: 500) as Integer)) } catch (Throwable ignored) { return 500 }
}

// ---------------- Notification and utility ----------------

void notifyOverallStatusChange(String state, Boolean forceNotify = false, Boolean stateChanged = false) {
    String normalised = (state ?: "").toString().toLowerCase()
    if (!(normalised in ["occupied", "empty"])) return

    String override = manualOverrideValue()
    Boolean forced = (override == normalised)
    String notificationKey = "${normalised}:${forced ? 'forced' : 'normal'}"
    String lastKey = (atomicState.lastNotifiedOverallState ?: "").toString()
    Boolean notificationStateChanged = lastKey != notificationKey

    if (!forceNotify && !stateChanged && !notificationStateChanged) {
        logDebug("Overall status notification not sent because committed state and notification state are unchanged: ${normalised}")
        return
    }

    String message = occupancyNotificationText(normalised)
    Integer sentCount = notifyAll(message)

    if (sentCount > 0) {
        atomicState.lastNotifiedOverallState = notificationKey
        atomicState.lastNotificationText = message
        atomicState.lastNotificationTime = timestamp()
        logInfo("Overall status notification sent: ${message} to ${sentCount} device(s)")
    } else {
        logWarn("Overall status changed to ${normalised}, but no notification device accepted the message '${message}'. Check Notification Configuration and use Test Notification.")
    }
}

String occupancyNotificationText(String state) {
    String value = (state ?: "").toString().toLowerCase()
    String override = manualOverrideValue()
    Boolean forced = (override == value)
    if (value == "occupied") return forced ? "Occupied (Forced)" : "Occupied"
    if (value == "empty") return forced ? "Departed (Forced)" : "Departed"
    return value
}

String guestModeOnNotificationText() {
    String untilText = (atomicState.guestModeUntilText ?: "").toString()
    if (untilText) return "Guest Mode on until ${untilText}"
    Long until = (atomicState.guestModeUntil ?: 0L) as Long
    if (until && until > 0L) return "Guest Mode on until ${formatEpochMs(until)}"
    return "Guest Mode on until planned time"
}

void sendTestNotification() {
    String msg = "Presence Manager test notification"
    Integer sentCount = notifyAll(msg)
    if (sentCount > 0) {
        atomicState.lastNotificationText = msg
        atomicState.lastNotificationTime = timestamp()
        logInfo("Test notification sent to ${sentCount} device(s)")
    } else {
        logWarn("Test notification was triggered, but no notification device accepted it. Check the selected Notification devices.")
    }
}

Integer notifyAll(String message) {
    String msg = safeMessage(message)
    List devices = asList(notificationDevices).findAll { it }
    Integer sentCount = 0
    List failures = []

    atomicState.lastNotificationAttemptText = msg
    atomicState.lastNotificationAttemptTime = timestamp()
    atomicState.lastNotificationAttemptDevices = devices.size()
    atomicState.lastNotificationAcceptedCount = 0
    atomicState.lastNotificationFailure = ""

    if (!devices) {
        atomicState.lastNotificationFailure = "No notification devices selected"
        logWarn("Notification requested but no notification devices are selected: ${msg}")
        return 0
    }

    devices.each { dev ->
        try {
            dev.deviceNotification(msg)
            sentCount++
        } catch (Throwable t) {
            String failure = "${dev?.displayName ?: 'unknown device'}: ${safeMessage(t.message)}"
            failures << failure
            logWarn("Notification failed for ${failure}")
        }
    }

    atomicState.lastNotificationAcceptedCount = sentCount
    atomicState.lastNotificationFailure = failures.join(" | ").take(300)
    return sentCount
}

String notificationDeviceSummaryText() {
    List devices = asList(notificationDevices).findAll { it }
    if (!devices) return "none selected"
    return "${devices.size()} selected: " + devices.collect { it?.displayName ?: "unnamed device" }.join(", ")
}

String safeCurrentValue(dev, String attr) {
    try { return (dev?.currentValue(attr) ?: "").toString() } catch (Throwable ignored) { return "" }
}

String timestamp() {
    try { return new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone) } catch (Throwable ignored) { return new Date().toString() }
}

String formatEpochMs(Long ms) {
    if (!ms) return ""
    try { return new Date(ms).format("yyyy-MM-dd HH:mm:ss", location.timeZone) } catch (Throwable ignored) { return new Date(ms).toString() }
}

String formatEpochMsShort(Long ms) {
    if (!ms) return ""
    try { return new Date(ms).format("HH:mm dd/MMM/yy", location.timeZone) } catch (Throwable ignored) { return new Date(ms).toString() }
}

String safeMessage(String value) {
    return (value ?: "").toString().replaceAll("[\r\n\t]", " ").take(300)
}

String html(value) {
    return (value ?: "").toString()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

void logDebug(String msg) {
    if (debugLogging) log.debug msg
}

void logInfo(String msg) {
    log.info msg
}

void logWarn(String msg) {
    log.warn msg
}
