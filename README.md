# Jabra Extension Hub

Android app jeta HTML/JS plugin (ZIP) import kore native-vabe chalay.

- Package: `com.jabra.hub` · minSdk 26 · targetSdk 34
- Build: GitHub Actions (`Android Build`) -> Actions > run > Artifacts > APK
- Plugin format: ZIP-er vitore `manifest.json` (`id`, `name`, `version`, `description`, `icon`, `entry`) + `index.html`
- Plugin JS API: `JabraAPI.getItem/setItem/removeItem/toast/info`

## Update korar niyom
Root-e `.zip` upload korle `Unpack ZIP` workflow auto extract kore commit kore dey.
Secret/API key/signing key repo-te rakhbe na.
