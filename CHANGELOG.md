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
