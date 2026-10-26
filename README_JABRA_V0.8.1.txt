Jabra Extension Hub v0.8.1 - Floating bubble and clipboard scan

NEW:
 - Menu (top-right) -> "Scan copied text (clipboard)": reads what you copied and opens Novel Assistant.
 - Menu -> "Floating bubble: turn ON": a draggable "J" bubble over other apps. Copy text anywhere, tap J, it scans.
   Needs the "Display over other apps" permission (Android opens that screen); turn the bubble on again after allowing.
 - Plugin API: JabraAPI.scanClipboard() (capability "clipboardScan").
Limits: Android only lets the app read the clipboard while Jabra is in front, so a tap briefly opens Jabra.
Not built or device-tested in this package.
