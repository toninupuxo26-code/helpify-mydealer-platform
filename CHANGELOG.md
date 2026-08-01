## 0.17.0 — Android form drafts and templates

- Added automatic persistence and restoration of live-action form drafts.
- Added named reusable templates per action and user role.
- Added template application, replacement and deletion.
- Added successful-action draft cleanup while preserving failed submissions.

## 0.16.0 — Android deep links and quick navigation

- Added notification navigation to unread server events.
- Added dynamic launcher shortcuts for events, favourites, server data and sync.
- Added Helpify and MyDealer custom deep links.
- Added browsable HTTPS dashboard links and singleTop intent handling.

## 0.15.0 — Android background synchronization

- Added opt-in periodic WorkManager synchronization.
- Added network and battery execution constraints.
- Added one-time background synchronization from the events section.
- Added background cache, change detection and notification updates.

## 0.14.0 — Android server change notifications

- Added detection of new server cards and workflow status changes.
- Added an in-app events section with read state and filtering.
- Added configurable Android summary notifications.
- Suppressed notifications during the first synchronization baseline.

## 0.13.0 — Android favourites, recent views and sharing

- Added persistent favourites for server and guided cards.
- Added a favourites-only dashboard filter.
- Added a recent views section with twenty entries per role.
- Added card sharing through the Android share sheet.

## 0.12.0 — Android offline cache, search and history

- Added read-only fallback to the last successful server dashboard.
- Added search and dynamic section filters.
- Added an actionable-card filter.
- Added persistent server-action history with timestamps and results.

## 0.11.0 — Android live forms and data packs

- Replaced fixed quick-action payloads with validated Android forms.
- Added explicit confirmations for workflow state changes.
- Added server-side Helpify task and offer data packs.
- Added server-side MyDealer product and cart data packs.

## 0.10.0 — Android live workflows

- Connected both Android dashboards to the authenticated server APIs.
- Added role-aware Helpify task and offer quick actions.
- Added MyDealer catalogue, cart, checkout and order quick actions.
- Preserved guided local scenarios as an offline demonstration layer.

## 0.9.0 — Interactive Android scenarios

- Added role-specific Helpify and MyDealer datasets.
- Added interactive multi-step scenarios with persistent progress.
- Added metrics, grouped sections and scenario reset controls.
- Updated the Android applications and APK artifacts to version 0.9.0.

## 0.8.3 — Android build memory profile

- Limited Gradle heap and worker count for VPS builds.
- Disabled parallel and incremental Kotlin compilation.
- Changed Helpify and MyDealer builds to sequential execution.
- Added memory diagnostics and optional build swap.

## 0.8.3 — Android Kotlin build fix

- Corrected invalid newline literals in the shared Android authentication and dashboard screens.
- Added a Kotlin source Doctor.
- Updated Android application and artifact version to 0.8.3.

## 0.8.2 — Android build pipeline

- Added a pinned Docker build environment for the native Android applications.
- Added repeatable Helpify and MyDealer debug APK generation.
- Added APK metadata, SHA-256 manifests and build verification.
- Updated Android package version to 0.8.2.

# Changelog

## 0.8.1 — Public platform baseline

- Published the Helpify and MyDealer monorepository.
- Added native Android source modules for both products.
- Added Laravel APIs, web applications and public landing pages.
- Added project documentation, security policy and contribution guidelines.
- Added repository quality checks and a clean release workflow.
