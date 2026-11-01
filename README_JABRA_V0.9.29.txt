Jabra Extension Hub v0.9.29 - Quiet Pill step H7b: icons, empty state, haptics, welcome

CHANGED:
 - "Save page" in the browser menu has its own icon (new q_ic_save_page.xml, page with folded corner). Downloads keeps the arrow.
 - Downloads empty state now has a large icon above the text (compound drawable, no layout change).
 - Light haptic tick (Q.tick) added to: main bottom navigation, Import FAB, home "Search plugins" / "Import plugin" / "Scan copied text", Downloads filter chips and row buttons, and the switches in Settings.
 - New file Onboarding.kt: a one-time welcome sheet (Plugins / Browser / Scan copied text) on a FRESH install only. People who already had the app never see it. To see it again: clear app data.
 - Version 0.9.29 (code 49).

Changed files: BrowserActivity.kt (1 line), MainActivity.kt, DownloadsActivity.kt, SettingsActivity.kt (small patches), Onboarding.kt (new), q_ic_save_page.xml (new), build.gradle.kts, README.
Not done (left): motion/transition pass (screen transitions, list item animations), Bookmarks export/import into the bookmarks manager, empty states of the Bookmarks/History dialogs (still plain text).
Not built, not run, not rendered.
Please check: fresh install shows the welcome sheet once, "Get started" and swipe-down both stop it from coming back; Save page icon in the menu; Downloads empty state icon in light and dark; haptic feels right and does not fire when a screen opens by itself (Settings switches).
