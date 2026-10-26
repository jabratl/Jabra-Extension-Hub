Jabra Extension Hub v0.9.0 - In-app updates

NEW:
 - Menu -> "Check for app update": finds the newest APK on GitHub Releases, downloads it, checks its SHA-256 and opens Android's installer.
   First time only, Android asks you to allow installs from Jabra.
 - Menu -> "Plugin store - updates": reads the plugin catalog and lets you install new plugins or update installed ones in one step.
   Existing plugin update/rollback and saved data are kept (it uses the normal plugin installer).
 - The app checks quietly at most every 12 hours and tells you when something is available.
 - Release builds are published to GitHub Releases automatically (needs the new android-build.yml and the stable signing key).
 - Drop plugin ZIPs into the repo folder store/ and the "Publish plugin store" workflow rebuilds the catalog.
Requires: the GitHub repository must be public (or releases hosted publicly).
Not built or device-tested in this package.
