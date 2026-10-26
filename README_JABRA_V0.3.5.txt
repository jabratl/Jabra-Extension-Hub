Jabra Extension Hub v0.3.5 source

Android Kotlin + WebView plugin hub. Build with GitHub Actions (Java 17, Gradle 8.7).

New in v0.3.5:
- App-private, per-plugin durable key/value storage exposed through JabraAPI.storageGet/storageSet/storageRemove/storageKeys/storageRemovePrefix.
- Standard localStorage calls are intercepted at document start and stored in app-private files, scoped to each plugin, with migration fallback from existing WebView localStorage.
- Plugin data survives activity/app exits and phone restarts.
- Plugin ZIP update does not delete plugin data; explicitly removing a plugin removes its SharedPreferences but currently leaves its plugin_data directory unless manually cleaned, to reduce accidental data loss.

Build success must be confirmed in GitHub Actions. Uninstalling the app or clearing its app data removes private storage. Export backups for important user data.
