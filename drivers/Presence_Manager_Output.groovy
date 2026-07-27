/*
 * Presence Manager Output
 * Namespace: Hubitat Integrations
 * Version: 4.3.2
 * Release: Broader beta package.
 *
 * Child driver for the Presence Manager application main status output.
 * on  = occupied / present / contact open / motion active
 * off = empty / not present / contact closed / motion inactive
 */

metadata {
    definition(
        name: "Presence Manager Output",
        namespace: "Hubitat Integrations",
        author: "Gordon Thelander"
    ) {
        capability "Switch"
        capability "PresenceSensor"
        capability "ContactSensor"
        capability "MotionSensor"

        attribute "occupied", "string"
        attribute "confidence", "number"
        attribute "guestMode", "string"
        attribute "lastReason", "string"
        attribute "lastEvidence", "string"
        attribute "lastChanged", "string"

        command "markPresent"
        command "markAway"
        command "setReason", [[name: "Reason*", type: "STRING", description: "Reason text"]]
        command "setPresenceManagerState", [
            [name: "Occupancy*", type: "STRING", description: "occupied or empty"],
            [name: "Confidence*", type: "NUMBER", description: "0-100"],
            [name: "Evidence", type: "STRING", description: "Evidence summary"],
            [name: "Reason", type: "STRING", description: "Decision reason"],
            [name: "Guest Mode", type: "BOOL", description: "Guest Mode active"]
        ]
    }

    preferences {
        input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false, required: false
    }
}

def installed() {
    initialize()
}

def updated() {
    initialize()
}

void initialize() {
    if (device.currentValue("switch") == null) off()
}

void on() {
    sendPresenceState(true)
}

void off() {
    sendPresenceState(false)
}

void markPresent() {
    on()
}

void markAway() {
    off()
}

void setReason(String reason) {
    sendEvent(name: "lastReason", value: (reason ?: "").toString().take(300))
}

void setPresenceManagerState(String occupancy, Object confidenceValue = 0, String evidence = "", String reason = "", Object guest = false) {
    Boolean occupiedNow = (occupancy ?: "").toString().equalsIgnoreCase("occupied")
    sendPresenceState(occupiedNow)

    Integer conf = 0
    try { conf = Math.max(0, Math.min(100, confidenceValue as Integer)) } catch (Throwable ignored) { conf = 0 }

    sendEvent(name: "confidence", value: conf)
    sendEvent(name: "lastEvidence", value: (evidence ?: "").toString().take(300))
    sendEvent(name: "lastReason", value: (reason ?: "").toString().take(300))
    sendEvent(name: "guestMode", value: truthy(guest) ? "active" : "inactive")
}

void sendPresenceState(Boolean occupiedNow) {
    if (occupiedNow) {
        sendEvent(name: "switch", value: "on")
        sendEvent(name: "presence", value: "present")
        sendEvent(name: "contact", value: "open")
        sendEvent(name: "motion", value: "active")
        sendEvent(name: "occupied", value: "true")
    } else {
        sendEvent(name: "switch", value: "off")
        sendEvent(name: "presence", value: "not present")
        sendEvent(name: "contact", value: "closed")
        sendEvent(name: "motion", value: "inactive")
        sendEvent(name: "occupied", value: "false")
    }

    sendEvent(name: "lastChanged", value: timestamp())
    logDebug("Set ${occupiedNow ? 'occupied/present' : 'empty/not present'}")
}

Boolean truthy(value) {
    if (value == null) return false
    if (value instanceof Boolean) return value
    String s = value.toString().toLowerCase()
    return s in ["true", "1", "yes", "on", "active"]
}

String timestamp() {
    try { return new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone) } catch (Throwable ignored) { return new Date().toString() }
}

void logDebug(String msg) {
    if (debugLogging) log.debug msg
}
