/*
 * Presence Manager Guest Mode Switch
 * Namespace: Hubitat Integrations
 * Version: 4.3.4
 * Release: Broader beta package.
 *
 * Child switch used by Presence Manager to enable or disable Guest Mode.
 */

metadata {
    definition(
        name: "Presence Manager Guest Mode Switch",
        namespace: "Hubitat Integrations",
        author: "Gordon Thelander"
    ) {
        capability "Switch"
        attribute "lastChanged", "string"
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
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "lastChanged", value: timestamp())
    logDebug("Guest Mode on")
}

void off() {
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "lastChanged", value: timestamp())
    logDebug("Guest Mode off")
}

String timestamp() {
    try { return new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone) } catch (Throwable ignored) { return new Date().toString() }
}

void logDebug(String msg) {
    if (debugLogging) log.debug msg
}
