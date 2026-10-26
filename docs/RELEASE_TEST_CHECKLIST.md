# Release test checklist

Use this after each GitHub Actions APK build. Source checks are not a substitute for an Android build or device test.

## Build and identity
- [ ] GitHub Actions `assembleDebug` completes successfully.
- [ ] Downloaded artifact name includes the current version, e.g. `Jabra-Extension-Hub-v0.5.0-debug`.
- [ ] APK filename includes the same version.
- [ ] Launcher label is `Jabra Extension Hub` (not `app-debug`).
- [ ] About dialog reports the same version as `app/build.gradle.kts` (0.5.0 for this release).

## Install/update safety
- [ ] Existing installation updates without uninstalling or clearing app data.
- [ ] Existing Novel Assistant plugin opens.
- [ ] Importing the same valid plugin as an update preserves its plugin ID and saved data.
- [ ] After a successful update, the previous version is available from the plugin menu; rollback restores it and keeps saved data.
- [ ] Invalid JSON, missing manifest, missing entry file, duplicate paths, path traversal, empty ZIP and oversized packages fail with an error and do not remove the existing plugin.
- [ ] A nested ZIP wrapper with exactly one manifest installs; an archive with multiple manifest candidates is rejected.

## Extension Manager
- [ ] Add/remove favourite; favourite survives app restart.
- [ ] A–Z, Z–A and favourites-first sorting work.
- [ ] Category filter works; filter/sort selections survive app restart.
- [ ] Existing plugins without category metadata still install and show as Other.

## Data and recovery
- [ ] Glossary JSON export creates a readable backup.
- [ ] Android Sharesheet opens from Share.
- [ ] Import/Restore accepts the backup and restores expected glossary entries.
- [ ] Glossary/settings remain after force-close and relaunch.
- [ ] Failed import leaves the previous plugin launchable.

## Diagnostics
- [ ] Import/export failures appear under App menu → Diagnostics & support.
- [ ] Share report opens Android Sharesheet only after user taps Share report.
- [ ] Clearing diagnostics does not remove plugin code or saved data.

## Trust boundary
- [ ] Confirm only trusted plugin ZIPs are installed. ZIP validation protects against common archive path/size attacks but does not make arbitrary plugin JavaScript trustworthy.


## API v1 compatibility and developer tools (v0.5.0)
- [ ] App menu → Developer tools · API contract shows API v1 and current host version.
- [ ] Sharing the API contract opens Android Sharesheet only after explicit user action.
- [ ] Existing plugins still see the legacy `info()` boolean fields and API version 1.
- [ ] Sample plugin reports current host version and runs without errors.
- [ ] `recordDiagnostic` creates a local diagnostic line and never uploads automatically.
- [ ] A plugin declaring `minJabraApiVersion: 2` is rejected before replacing an existing installation.
- [ ] A plugin declaring an unknown `requiredCapabilities` item is rejected with an actionable message.
- [ ] A valid plugin using `minJabraApiVersion: 1` and known required capabilities installs successfully.
- [ ] GitHub Actions source validation passes before Gradle build.
