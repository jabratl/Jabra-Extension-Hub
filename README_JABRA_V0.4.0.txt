Jabra Extension Hub v0.4.0 — Stability & Extension Manager Foundations

Release purpose
This release combines a focused Phase 1 stability/data-safety pass with the low-risk foundations of Phase 2 so the user does not need to build/install a separate APK for each small phase.

Phase 1 — Stability & data safety
- Hardened plugin ZIP import against path traversal, absolute paths, backslash paths, duplicate entry paths, empty archives, ambiguous/missing manifests, oversized manifests, excessive entry counts, and excessive uncompressed size.
- Validates required manifest name/version/id values and entry-file paths before replacing an installed plugin.
- Accepts a manifest wrapped in up to three nested folders, provided exactly one manifest candidate exists.
- Stages new plugin files before replacing the current install.
- Retains a recovery copy when restoration fails rather than intentionally deleting the only remaining previous version.
- Uses actionable import errors instead of a generic failure for common invalid-package cases.
- Existing plugin data remains separate from plugin code and is not erased by an update.

Phase 2 — Extension Manager foundations
- Adds optional manifest category metadata; missing/unknown categories fall back to Other.
- Displays plugin version and category in the list and details view.
- Adds persistent favourites.
- Adds filters for favourites and available categories.
- Adds A–Z, Z–A and favourites-first sorting.
- Retains one previous plugin version after a successful update and adds a user-confirmed rollback action; rollback swaps code versions while keeping plugin-owned data scoped to the same plugin ID.
- Persists manager filter/sort settings across app restarts.

Release identity
- Android versionCode: 12
- Android versionName: 0.4.0
- Launcher label remains Jabra Extension Hub.
- GitHub Actions derives the APK filename and artifact name from versionName, producing Jabra-Extension-Hub-v0.4.0-debug.apk for this release.

Manifest category field (optional)
Example: "category": "Utility"
Recognized values: Character, Item, Skill, Place, Organization, Title, Other, Utility, Writing, Translation, Developer.
Existing plugins without this field continue to install and are shown as Other.

Security/compatibility notes
- This is not a full sandbox for malicious JavaScript. Plugin code still runs in WebView with the documented Jabra bridge; only install-package path/manifest validation is hardened here.
- Existing plugin IDs are still derived/sanitized from manifest id or name. Do not rename an existing plugin ID casually: saved data is scoped by plugin ID.
- No automatic online update/store has been introduced yet; that depends on a stronger trust, signature/hash, version comparison, backup and rollback design.
- Keep the v0.3.8 API v1 docs/helper and the existing native export/share/persistent-data behavior.

Validation status
Static checks are performed on ZIP structure, JSON/XML/workflow text, source-level invariants and JavaScript where applicable. This environment does not have a configured Android Gradle build, emulator or user's phone attached; a successful Android build and on-device runtime test must not be claimed until GitHub Actions and the device test pass.

Recommended device test before treating this as stable
1. Build and install the APK; confirm launcher label and About version 0.4.0.
2. Import the existing Novel Assistant ZIP and open it.
3. Favourite it, restart the app, and confirm favourite remains.
4. Try each sort and category/favourite filter; restart and confirm settings persist.
5. Import a valid updated plugin ZIP and confirm plugin saved data remains.
6. Try an invalid/empty ZIP and confirm a clear error appears and the existing plugin still opens.
7. Export/share/restore glossary backup and verify the four already-confirmed workflows still work.
