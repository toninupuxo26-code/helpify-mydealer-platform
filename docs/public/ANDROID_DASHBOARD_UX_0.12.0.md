# Android dashboard UX 0.12.0

## Offline live-data cache

Each application stores the last successful server dashboard separately for
each role. Cached cards are displayed when the network is unavailable.

Cached cards are read-only. Server actions remain disabled until a fresh
authenticated payload is loaded.

## Search and filters

The shared dashboard supports:

- text search across titles, descriptions, details, sections and badges;
- dynamic section filters;
- an actionable-cards-only filter;
- one-tap reset of all filters.

Search applies to server cards, guided scenarios and action history.

## Action history

The most recent forty server actions are retained locally. The dashboard shows
the latest entries with timestamp, result and success marker. History can be
cleared from the application.
