# Jabra Extension API v1 — Compatibility Contract

This document defines the supported host API for extensions running in Jabra Extension Hub. Do not assume that the host is a Capacitor app, Chrome extension runtime, or ordinary desktop browser.

## Compatibility rules

1. Feature-detect every native API at runtime. Never assume a method exists just because another extension used it.
2. Read `JabraAPI.info()` and/or `JabraAPI.capabilities()` before relying on optional features.
3. Keep browser fallbacks where safe. If no safe fallback exists, show a clear unsupported-feature message; do not silently claim success.
4. Keep import and export separate and test both. Also test share, backup restore, malformed files, cancel actions, and large files.
5. Do not depend on Capacitor APIs (`Capacitor`, `Filesystem`, `Share`) unless the extension explicitly targets a separate Capacitor host.
6. Do not use `file://` URLs or arbitrary local paths. Use the host file API, a user-selected file, or a `content://` URI managed by Android.
7. Never overwrite or delete user data during import without explicit confirmation. Validate backups before changing current data.
8. Store extension data only under its own scoped keys. Do not attempt to read another extension's private storage.
9. Keep extension manifests backward-compatible. Unknown optional fields may be ignored; unknown required permissions/features must be handled explicitly by the host.

## API v1 methods currently provided by the Hub

| Method | Contract | Notes |
|---|---|---|
| `info()` | Returns JSON text describing host, API version and capabilities | Parse with `JSON.parse` inside `try/catch` |
| `capabilities()` | Returns a JSON array as text | Feature detection; do not rely only on version strings |
| `storageGet(key)` | Returns string or null | Per-extension private storage |
| `storageSet(key, value)` | Returns boolean | Keep values reasonably sized; check result |
| `storageRemove(key)` | Returns boolean | Scoped to current extension |
| `storageKeys(prefix)` | Returns JSON array text | Prefix-filtered keys for current extension |
| `storageRemovePrefix(prefix)` | Returns boolean | Destructive; use only for your own namespace |
| `getItem(key)` / `setItem(key,value)` / `removeItem(key)` | Legacy simple settings API | Prefer `storage*` for new extensions |
| `exportBase64File(name, base64, mime)` | Returns boolean | Saves generated data to Downloads on supported Android versions; check result |
| `shareBase64File(name, base64, mime)` | Returns boolean | Opens Android Sharesheet with a content URI; user still chooses destination |
| `copyText(text)` | Returns whether request was accepted | Clipboard work happens on Android main thread; a true return does not guarantee the user kept the clipboard contents |
| `shareText(text, title)` | Returns whether request was accepted | Opens system Sharesheet |
| `toast(message)` | Shows short Android message | Optional convenience only |
| HTML `<input type="file">` | Opens Android document picker through the WebView file chooser | Use FileReader to read the selected file; handle cancel/error |

## File export fallback pattern

```js
async function blobToBase64(blob) {
  return await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
    reader.onerror = () => reject(reader.error || new Error('Could not read export data'));
    reader.readAsDataURL(blob);
  });
}

async function saveBlob(name, blob) {
  const api = window.JabraAPI;
  if (api && typeof api.exportBase64File === 'function') {
    const ok = api.exportBase64File(name, await blobToBase64(blob), blob.type || 'application/octet-stream');
    if (!ok) throw new Error('Hub export failed; try again or use Share as an alternative.');
    return { ok: true, method: 'hub-export' };
  }
  // Browser fallback: not guaranteed in every embedded WebView.
  const url = URL.createObjectURL(blob);
  try {
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    a.remove();
    return { ok: true, method: 'browser-download', fallbackMayVary: true };
  } finally {
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  }
}
```

## Share fallback pattern

```js
async function shareBlob(name, blob) {
  const api = window.JabraAPI;
  if (api && typeof api.shareBase64File === 'function') {
    const ok = api.shareBase64File(name, await blobToBase64(blob), blob.type || 'application/octet-stream');
    if (!ok) throw new Error('Hub share failed; use Export instead.');
    return { ok: true, method: 'hub-share' };
  }
  const file = new File([blob], name, { type: blob.type || 'application/octet-stream' });
  if (navigator.canShare && navigator.canShare({ files: [file] }) && navigator.share) {
    await navigator.share({ files: [file], title: name });
    return { ok: true, method: 'web-share' };
  }
  return await saveBlob(name, blob);
}
```

## Import pattern

Use `<input type="file" accept=".json,application/json">`, then `FileReader` to read the file. Validate its schema, version, required fields and reasonable size before showing a preview. Do not replace live data until validation succeeds and the user confirms. Provide merge and replace as clearly separate actions when appropriate.

## Capability detection

```js
function getJabraHostInfo() {
  try {
    if (!window.JabraAPI || typeof JabraAPI.info !== 'function') return null;
    return JSON.parse(JabraAPI.info());
  } catch (_) {
    return null;
  }
}
function hasJabraCapability(name) {
  const info = getJabraHostInfo();
  return !!(info && Array.isArray(info.capabilities) && info.capabilities.includes(name));
}
```

## Not part of API v1 yet

These must not be assumed to exist: background execution, scheduled jobs, arbitrary URL downloads, push notifications, cloud sync, file-system browsing without user selection, cross-extension messaging, arbitrary clipboard read, Chrome `chrome.*` APIs, service workers as a Chrome extension runtime, and privileged access to other apps. Add them only through a documented API version/capability with explicit permission and tests.

## Release acceptance tests for every extension

- Install and launch from a clean state.
- Test on the Hub's minimum supported Android version and a current Android version when available.
- Test storage write/read after app restart.
- Test import valid, invalid, oversized, and cancelled files.
- Test export, share, and restore independently.
- Test missing optional API and browser fallback.
- Test denied permissions, offline mode, and interrupted operations.
- Confirm failures are visible and never reported as success.
