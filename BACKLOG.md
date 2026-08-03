# Backlog

Feature ideas and known limitations that have been discussed but not implemented.
Nothing here ships until Gordon says so.

## Presence Report - add day-of-week abbreviation before the date

On the Presence Report table, each row's date currently shows as plain
"2026-08-02" - add a "Mon, Tue, Wed..." style day abbreviation before it, e.g.
"Mon 2026-08-02". Implementation note: `presenceReportDayLabel(Long dayStartMsValue)`
at Presence_Manager.groovy:3241 is the single place that formats the date string
("yyyy-MM-dd" via `location.timeZone`) - change the format pattern there (e.g.
prepend "EEE " -> "EEE, yyyy-MM-dd") rather than touching the caller at line 3375,
which just appends the "(today, so far)" suffix.

(The "approximate address" item that used to be here shipped in 4.4 as the
"Location Lookup" line - reverse-geocodes location.latitude/location.longitude
via OpenStreetMap Nominatim, off by default, manual lookup only.)
