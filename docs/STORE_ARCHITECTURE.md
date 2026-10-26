# Jabra Extension Store — implementation plan (not a live store yet)

This release establishes compatibility metadata and source validation needed before remote distribution. It does **not** fetch a remote catalog, host packages, or perform automatic online updates.

## Recommended architecture

1. **Catalog document**: a versioned JSON index hosted at a stable HTTPS URL. Catalog entries contain a stable plugin ID, name, current version, short description, category, package URL, SHA-256 digest, minimum Hub version/API version, required capabilities, release notes, and publisher identity.
2. **Package integrity**: download to a temporary file, enforce compressed and uncompressed size/entry limits, verify SHA-256 against the catalog, then validate manifest ID/version/API requirements. Hash matching proves the bytes match the catalog, not that the publisher is trustworthy.
3. **Publisher trust**: before broad distribution, add signed catalog/package metadata with a public key pinned in the app or a documented key-rotation scheme. Never treat an unsigned remote JSON index as sufficient trust.
4. **Update preview**: show installed/current versions, changelog, package size, required capabilities, and data-migration notes before the user confirms.
5. **Transactional install**: stage → validate → preserve current version → install → smoke-check manifest → commit. If any step fails, restore the previous version and retain a diagnostic event.
6. **Data migration**: plugin code updates must not erase plugin-scoped data. Any schema migration must be opt-in, versioned, recoverable, and tested against a backup. Rollback of code cannot automatically reverse arbitrary data migrations, so migrations need explicit compatibility rules.
7. **Privacy and permissions**: plugin metadata must never grant native capabilities. Capability requirements are compatibility declarations only. Do not upload usage, diagnostics, glossary content, or saved plugin data by default.
8. **Offline-first behavior**: installed plugins must continue to work without network access. Store unavailability must never block launching locally installed extensions.

## Catalog schema sketch

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-10-26T00:00:00Z",
  "plugins": [
    {
      "id": "example-plugin",
      "name": "Example Plugin",
      "version": "1.2.0",
      "category": "Utility",
      "description": "A short description",
      "minHubVersion": "0.5.0",
      "minJabraApiVersion": 1,
      "requiredCapabilities": ["storage"],
      "packageUrl": "https://example.invalid/plugins/example-plugin-1.2.0.zip",
      "sha256": "REPLACE_WITH_64_HEX_CHAR_SHA256",
      "publisher": { "id": "publisher-id", "name": "Publisher Name" },
      "releaseNotes": "What changed in this release"
    }
  ]
}
```

The `.invalid` URL and digest placeholder above are illustrative only and must never be used as a real catalog entry.

## Version comparison

Do not compare plugin versions as raw strings (`1.10` sorts before `1.9` lexically). Adopt a documented semantic-version parser with a safe fallback for legacy plugin versions. If a version is unparseable, show it as “version comparison unavailable” and require a manual user decision rather than silently downgrading or updating.

## Release gates before enabling automatic updates

- [ ] Catalog schema validator and malformed-catalog tests.
- [ ] HTTPS-only package downloads, bounded timeouts and cancellation.
- [ ] SHA-256 verification and signed publisher metadata.
- [ ] No install on ID mismatch, version mismatch, unsupported API or missing required capability.
- [ ] Interrupted-download cleanup and no partial install.
- [ ] Existing plugin and data preserved on every failure path.
- [ ] Explicit update preview and rollback availability.
- [ ] Offline installed plugins still launch.
- [ ] Security review of WebView bridge and plugin-origin restrictions.
- [ ] Test upgrade from the currently installed APK without clearing app data.
