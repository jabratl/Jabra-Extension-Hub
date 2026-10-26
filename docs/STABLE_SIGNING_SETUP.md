# Stable APK signing setup (one time)

## Why this is required

Android permits an installed app to be updated only when the new APK has the same application ID and is signed by the same certificate. GitHub-hosted debug builds commonly receive a new temporary debug key on each clean runner, which can cause `App not installed as package conflicts with an existing package`.

## Create the signing key

**Security note:** Run the `Generate Jabra release signing key (one-time)` workflow only in a repository whose access is restricted to people you trust. Its short-lived artifact contains the private key and passwords. Do not commit the artifact or share it.

1. Open GitHub → your repository → Actions.
2. Select `Generate Jabra release signing key (one-time)` and choose **Run workflow**. This does not build an APK.
3. Open that workflow run, download artifact `Jabra-private-signing-setup`, and extract it on a trusted device.
4. Open `GITHUB_SECRETS.txt`. In repository **Settings → Secrets and variables → Actions**, create four repository secrets using the exact names and values in that file: `JABRA_SIGNING_KEYSTORE_BASE64`, `JABRA_SIGNING_STORE_PASSWORD`, `JABRA_SIGNING_KEY_ALIAS`, `JABRA_SIGNING_KEY_PASSWORD`. Keep the file private.
5. Store `jabra-release.p12` in a secure personal backup location that is not the source repository. If the keystore and passwords are lost, future APK updates signed by that key cannot be produced.
6. Delete the setup artifact after the secrets and secure backup are confirmed.
7. Run the normal `Android Build` workflow. It should produce `Jabra-Extension-Hub-v0.6.0.apk` (release-signed), not a `-debug.apk` file. Check the workflow log says `Stable release signing configured`.

## One-time migration from a previous debug build

If the installed APK was signed by a different debug key, Android will still reject the first stable release APK. Before uninstalling, export the Novel Assistant glossary and save plugin ZIPs and any other data you need. Then uninstall the old Hub, install the stable release APK, re-import plugins and restore backups. This one-time migration is unavoidable when the previous signing certificate is unavailable. After that, future releases built with the same four secrets can update in place and preserve app-private data.

## Workflow behavior

- All four signing secrets present: builds a stable release APK.
- None present: builds a temporary debug APK and prints a warning that it cannot reliably update another build.
- Only some secrets present: fails early rather than silently creating an incompatible APK.

Do not use a new signing key for each release. Never publish the private keystore or its passwords.
