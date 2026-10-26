Jabra Extension Hub v0.3.4 — source project

Fix in this update:
- Fixes a Kotlin compilation error by importing MaterialAlertDialogBuilder in PluginActivity.kt, where JavaScript alert/confirm/prompt dialogs are used.
- Retains the v0.3.3 storage screen, cache clearing, plugin ZIP export, staged plugin update, and WebView file chooser support.
- Bumps Android versionCode to 6 and versionName to 0.3.4.

Build notes:
- This is source code, not an APK. Build through GitHub Actions or Android Studio.
- This environment has not run a complete Android Gradle build or real-device test, so GitHub Actions must confirm whether any additional compiler errors remain.
- Plugin export creates a portable plugin ZIP. It does not include app-level private preferences belonging to that plugin.
- Storage figures cover installed plugin files and app cache, not the entire Android app footprint reported by system Settings.

GitHub Actions:
- The included .github/workflows/android.yml builds app-debug.apk when pushed to main/master or run manually from Actions.
- If your repository uses a different default branch, run the workflow manually from the Actions tab.
