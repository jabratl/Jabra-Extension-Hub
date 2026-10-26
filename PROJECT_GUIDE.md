# Jabra Extension Hub — Project Guide (v0.7.0 Phase 4 UI foundation)

## What this project is
A Kotlin Android app that hosts ZIP-based HTML/CSS/JavaScript extensions inside Android WebView. Package/application ID: `com.jabra.hub`. Current source version: `0.7.0` (versionCode 15).

## Build
The repository is designed for GitHub Actions using Java 17 and Gradle 8.7. Existing workflows may include `Unpack ZIP`, `Android Build`, and `Build APK`. Keep existing repository workflows unless deliberately reviewing their triggers. The app can build a temporary debug APK when release-signing secrets are absent. Stable signing is optional and was explicitly deferred by the project owner.

## Main source locations
- `app/src/main/java/com/jabra/hub/MainActivity.kt`: plugin list, search, sorting, favorites, import/export, diagnostics and settings.
- `app/src/main/java/com/jabra/hub/PluginActivity.kt`: WebView-based extension runtime and native API bridge.
- `app/src/main/java/com/jabra/hub/PluginStore.kt`: install/import, storage, update and rollback.
- `app/src/main/java/com/jabra/hub/JabraApiContract.kt`: documented native API capabilities/version.
- `app/src/main/assets/sample/`: sample extension and compatibility helper.
- `docs/EXTENSION_API_V1.md`: plugin/native API contract.
- `docs/STORE_ARCHITECTURE.md`: future store architecture notes.

## Extension package model
A plugin is imported as a ZIP containing a `manifest.json` and entry HTML file (usually `index.html`), plus optional JS/CSS/assets. The plugin uses the documented `JabraAPI` native bridge and should feature-detect capabilities. Do not assume Chrome extension APIs, persistent background workers, arbitrary cross-app webpage access, or cloud sync. Stable plugin IDs should not be casually changed because plugin data is scoped to them.

## Phase 4 UI foundation in this source
- Adds a compact bottom navigation surface: Home, Installed, Import, Activity and Settings.
- Home clears current search/filter state; Installed focuses the plugin list; Import launches Android's document picker; Activity opens local diagnostics; Settings exposes theme preference choices and storage/diagnostics shortcuts.
- Keeps existing search, category filtering, sorting, favorites, plugin details, import/export, remove and rollback behavior.
- Uses scrollable content and bottom padding to accommodate the navigation surface on narrow screens.

## Important limitations / next implementation work
This is a UI foundation, not a claim that every Phase 4 requirement is complete. Full Material 3 dynamic light/dark/system palettes, responsive tablet/landscape two-pane layout, real enable/disable runtime lifecycle, reload controls, per-extension compatibility badges, robust activity feed, and automatic raw-chapter glossary detection still need implementation and testing. A normal Android app cannot silently inspect another browser's webpage. External chapter intake should use Android Sharesheet (`ACTION_SEND`), `ACTION_VIEW`/an app-owned reader, or a separately authorized browser integration. Novel Assistant must expose a clear chapter text intake/detection action and must not scrape unrelated apps without consent.

## Safety and testing
Never place signing keys/passwords in source ZIPs or committed files. No Android Gradle build or device test is asserted by this package generation. Use GitHub Actions build results and test import/export, persistence, plugin open/reload, settings, and chapter handoff on-device before calling the release verified.


## v0.8.0 chapter intake
`IntakeActivity` + `ChapterIntake.kt` receive selected text (PROCESS_TEXT), shared text/links (a lone link is fetched and converted to text), and .txt/.docx/.epub/.html files, store them in `filesDir/intake/pending.json`, and open the plugin `jabra-novel-assistant`. Plugins read it with `JabraAPI.intakeGet()`. Not yet verified by an Android build or on a device. Remaining: PDF, clipboard button inside the Hub, floating bubble.
