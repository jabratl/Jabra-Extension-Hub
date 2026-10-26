Jabra Extension Hub v0.3.3 — source project

New in this update:
- Storage & data screen: installed plugin count, plugin storage size, temporary cache size.
- Clear temporary cache without deleting installed plugins or plugin preferences.
- Export an installed plugin as a ZIP from its three-dot menu.
- About dialog describing the app and plugin limitations.
- Safer plugin update staging: the old installation is retained until the replacement is staged, with rollback where possible.
- Keeps WebView file chooser support for HTML file inputs; accept-type strings are normalized to MIME types.

Build notes:
- This is source code, not an APK. Build through GitHub Actions or Android Studio.
- Real-device Android testing has not been performed in this environment.
- Plugin export creates a portable plugin ZIP. It does not include app-level private preferences belonging to that plugin.
- Storage figures cover installed plugin files and app cache, not the entire Android app footprint reported by system Settings.

GitHub Actions:
- The included .github/workflows/android.yml builds app-debug.apk when pushed to main/master or run manually from Actions.
- If your repository uses a different default branch, you can still run the workflow manually from the Actions tab.
