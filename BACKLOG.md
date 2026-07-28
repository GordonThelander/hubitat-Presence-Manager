# Backlog

Feature ideas and known limitations that have been discussed but not implemented.
Nothing here ships until Gordon says so.

## Approximate address line on the main page

Add a line on `mainPage()`, near "Overall Status", something like:

`Approximate address: <reverse-geocoded street-level address>`

**Source data:** `location.latitude` / `location.longitude` - the hub's configured
home location, already available to any app, no new input needed.

**Lookup:** reverse geocode those coordinates via OpenStreetMap's Nominatim API
(`https://nominatim.openstreetmap.org/reverse?format=json&lat=...&lon=...`). No API
key required. Requirements are just good citizenship, not authentication:
- Send a real `User-Agent` header identifying the app (Nominatim's usage policy
  asks for this, it's not a technical gate).
- Stay within roughly 1 request/second - a total non-issue since this would only
  fire on page load, not on every evaluation cycle.
- Cache the result (e.g. in `atomicState`) rather than calling on every single main
  page render, since the coordinates never change - only look up once and reuse.

**Things to decide before building, not during:**
- Exactly how much of the returned address to show. Nominatim's reverse response
  includes a full structured breakdown (house number, road, suburb, city, postcode,
  country, etc.) - showing the full thing is more than "street level" and more
  precise than Gordon may want displayed on a page that could be screen-shared or
  viewed by anyone with dashboard access. Probably want to compose just road +
  suburb/locality, not house number or postcode, but confirm with Gordon rather than
  guessing.
- This sends the home's coordinates to an external third-party service (OSM) on
  first lookup. Low risk (OSM's a well-established open project, not ad-tech), but
  worth being explicit about since it's a real outbound data flow that doesn't exist
  in the app today - Presence Manager currently makes zero external HTTP calls.
- Should probably be an opt-in setting (default off) rather than always-on, given
  it's displaying an address on what's otherwise a purely internal-network app.
- Failure handling: Nominatim being unreachable/slow/rate-limited shouldn't block or
  slow down the main page rendering - needs a timeout and a graceful "address
  unavailable" fallback, not a hung page load.
