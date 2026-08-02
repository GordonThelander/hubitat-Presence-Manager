# Backlog

Feature ideas and known limitations that have been discussed but not implemented.
Nothing here ships until Gordon says so.

## Third Party Services picker - blue description text overlaps the device-picker button

On the config page, "Third Party Services config" section, the native Hubitat
`capability.switch` multi-select input's blue description text renders overlapping
the device-picker button. Cosmetic only - Gordon confirmed he can still tap through
to the button fine, just looks poor. Likely cause: the long `title:` text
("Third Party Services - Google Home, Alexa, SmartThings, etc.") made the native
label box taller than the platform expects for this widget type, pushing the
description up over the button. A shortened-title fix (title: "Third Party
Services switches", with the detail moved into description instead) is already
written and compiles clean in the Claude staging folder's Presence_Manager.groovy
- just not pushed yet since it's an unverified guess about Hubitat's own layout
engine and Gordon wants to batch it later rather than trigger another hub-paste
cycle for a cosmetic issue. Fold into whatever the next real release is.

(The "approximate address" item that used to be here shipped in 4.4 as the
"Location Lookup" line - reverse-geocodes location.latitude/location.longitude
via OpenStreetMap Nominatim, off by default, manual lookup only.)
