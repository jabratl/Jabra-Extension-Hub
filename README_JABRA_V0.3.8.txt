Jabra Extension Hub v0.3.8 — Extension API v1 Compatibility Foundation

Changes from v0.3.7:
- Adds JabraAPI API version and capability reporting.
- Adds native clipboard text copy (copyText) and Android Sharesheet text sharing (shareText).
- Adds docs/EXTENSION_API_V1.md with current API contracts, feature detection, safe fallbacks, import/export/share guidance, and unsupported API list.
- Adds app/src/main/assets/sample/jabra-compat.js as a reusable helper example for future extension authors.
- Keeps existing storage, file export, file share, file chooser, and crash-handling behaviour.
- Increments versionCode to 10 and versionName to 0.3.8.

Important:
- This source ZIP has not been built with Gradle or tested on a physical Android device in this session.
- The compatibility helper is an example asset; future plugin ZIPs must include it or port its helpers into their own code. It is not automatically injected into installed extensions.
- API v1 currently covers storage, file import through HTML file input, file export, file share, clipboard write, text share, toast, and capability reporting. Notifications, arbitrary downloads, cloud sync, background tasks, and Chrome extension APIs are NOT implemented yet.
- Build via GitHub Actions, then test API capability reporting, copy/share, backup export/import, plugin restart persistence, and existing plugins on device.
