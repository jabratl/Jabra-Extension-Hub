Jabra Extension Hub v0.3.7 — Export/Share Fix

Main changes:
- Adds native JabraAPI.exportBase64File(name, base64, mime) to save generated files to Downloads/Jabra Extension Hub on Android 10+.
- Adds native JabraAPI.shareBase64File(name, base64, mime) using Android Sharesheet and a FileProvider content URI.
- Configures FileProvider and reports export/share failures to the user.
- Preserves v0.3.6 crash handling and persistent storage changes.

IMPORTANT:
- This source ZIP has not been built or tested on a physical Android device in this session.
- After building, test Export backup, Share backup, Export glossary pack, Export Excel/CSV and translation TXT on-device.
- App-private data is still removed if the app is uninstalled or its data is cleared; export backups to user-accessible storage.
