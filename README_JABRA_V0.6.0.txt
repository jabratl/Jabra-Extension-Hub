Jabra Extension Hub v0.6.0 — Stable Signing Migration + Release Reliability

This release addresses the package-signature conflict encountered while installing v0.5.0 and prepares a stable path for future in-place updates. It keeps the API v1 foundation, local extension manager, diagnostics, plugin validation, and data-preserving workflows from v0.5.0.

Version identity
- Android versionName: 0.6.0
- Android versionCode: 14
- Launcher label: Jabra Extension Hub
- Stable signed artifact: Jabra-Extension-Hub-v0.6.0.apk
- Temporary debug fallback: Jabra-Extension-Hub-v0.6.0-debug.apk

What changed
- Gradle release signing is configured from environment variables and never embeds credentials in source.
- The main GitHub Actions workflow builds a stable release APK when all four JABRA_SIGNING_* repository secrets are configured.
- Missing all signing secrets falls back to a clearly labelled debug APK; partial secret configuration fails early.
- Added a manual one-time workflow to generate a PKCS12 keystore and short-lived private setup artifact, plus a step-by-step phone-friendly GitHub Secrets guide.
- Added pre-build checks for versioned APK identity and stable signing workflow expectations.

Important migration note
If the installed v0.5.0 APK used a different debug certificate, the first stable release may still require a one-time uninstall/reinstall. Back up glossary JSON, plugin ZIPs and any other needed data first. Once installed with the stable key, future releases must reuse the same keystore and secrets to update in place.

What is not included
- This release does not yet ship a live Online Extension Store or automatic online updater.
- Stable signing is not active until the four repository secrets are configured.
- Android Gradle build and real-device tests have not been run in this environment.

Validation
- Source validation and JavaScript syntax checks are run by GitHub Actions before Gradle build.
- Verify the action log says `Stable release signing configured` and the artifact has no `-debug` suffix before using it as the long-term update channel.
