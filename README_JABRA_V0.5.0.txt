Jabra Extension Hub v0.5.0 — API v1 Compatibility + Developer Tools

This release combines Phase 3 (Versioned Native API) foundations and Phase 5 (Developer Tools) foundations, while adding release-gate documentation for Phase 4 (Online Extension Store). It does not yet implement a live online store or automatic online updates.

Version identity
- Android versionName: 0.5.0
- Android versionCode: 13
- Launcher label: Jabra Extension Hub
- GitHub Actions artifact and APK filename are derived from versionName: Jabra-Extension-Hub-v0.5.0-debug.apk

Phase 3 — API v1 compatibility
- Adds JabraApiContract as the single source of truth for API version and supported capability names.
- Extends JabraAPI.info() with host version/package, Android API level, minimum supported API version and explicit limitations.
- Retains earlier v1 info() booleans to avoid breaking plugins built against the previous contract.
- Adds bounded local-only JabraAPI.recordDiagnostic(event, detail) for extension-originated diagnostic events.
- Adds optional manifest fields minJabraApiVersion and requiredCapabilities.
- Rejects plugins requiring a newer API or unknown required capabilities before replacing an installed plugin.
- Expands jabra-compat.js with capability checks, persistent storage helpers, file export/share, clipboard/text-share fallbacks and diagnostics.

Phase 5 — Developer tools foundations
- Adds App menu → Developer tools · API contract.
- Shows supported API capabilities and known runtime limitations; offers a user-triggered share action.
- Adds release source validation before Gradle build: JSON parsing, sample manifest schema, Android XML parsing, release identity checks and key-feature presence checks.
- Adds JavaScript syntax checks in GitHub Actions.

Phase 4 — Store architecture groundwork
- Documents a future catalog schema, hash/signature trust boundaries, semantic-version comparison, update preview, staged installation, rollback, data migration and release gates in docs/STORE_ARCHITECTURE.md.
- No remote catalog URL, package hosting, signature system, network installer or automatic updater is shipped in this release.

Backward compatibility
- Existing plugins that omit minJabraApiVersion default to API v1.
- Existing plugins that omit requiredCapabilities remain accepted.
- Legacy info() booleans remain present.
- Existing plugin IDs and plugin-scoped data locations are unchanged.
- Do not rename installed plugin IDs unless a deliberate data migration is implemented.

Security and privacy
- Capability declarations are compatibility metadata, not permissions and not a security certification.
- Plugin JavaScript is still code. Only install extensions from trusted sources.
- Diagnostics remain local unless the user explicitly chooses to share them.
- Do not log credentials, tokens, glossary contents or private documents.
- No cloud sync, background jobs, Chrome extension runtime, push notifications or arbitrary URL download API is implied.

Validation status
- Source-level validation and JavaScript syntax checks are included in GitHub Actions and have been run locally for this source bundle.
- Android Gradle compilation and real-device runtime testing have not been performed in this environment. Treat the APK as unverified until GitHub Actions succeeds and the device checklist is completed.

Recommended combined device test
1. Update-install over the existing Hub without clearing app data.
2. Confirm launcher name and About version 0.5.0.
3. Open Novel Assistant and verify glossary/settings remain.
4. Verify glossary export, Share Sheet, Import/Restore and persistence.
5. Open App menu → Developer tools · API contract and share the contract.
6. Test the bundled sample plugin and inspect its host info.
7. Try importing a test plugin requiring API v2; it must be rejected without replacing an installed version.
8. Try an unknown required capability; it must be rejected before installation.
9. Confirm valid API v1 plugin imports and plugin rollback remains available.
10. Confirm GitHub Actions source validation passes before Gradle build.
