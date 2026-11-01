Jabra Extension Hub v0.9.21 - Quiet Pill step H1: app shell

CHANGED (main app):
 - New bottom bar: Home, Plugins, Downloads, Browser. Home and Plugins switch inside the main screen; Downloads and Browser open their own screens (the selected tab stays on Home/Plugins). Back from Plugins goes to Home. The chosen tab survives rotation.
 - Home: title, a search pill (tap it to go to Plugins with the keyboard open), "N plugins installed", and two quick cards: Import plugin, Scan copied text.
 - Plugins: search, count, filter/sort, the plugin list and the Import plugin button (same list, menus and dialogs as before).
 - New Settings screen (gear on Home). It replaces the old three-dot menu, the old Settings dialog and the Activity tab. Contents: Floating bubble switch, Scan copied text, Plugin updates, Storage & data, Check for app update, Diagnostics & support, Developer tools / API contract, About. All dialogs keep their old text.
 - Theme choice (System/Dark/Light) is gone from Settings: the app is dark only, so the option did nothing. Light theme comes back in H7 if it is built for real.
 - Icons are vectors (new: q_ic_apps, q_ic_settings, q_ic_globe). No emoji or symbol characters in the main screen any more; the favourite mark in the plugin line is the word "Favourite".
 - activity_main.xml uses the q_* tokens (no more bg_search, bg_chip, ic_add, @color/bg...).
 - Updater: the quiet "plugin updates available" toast now says "Settings > Plugin updates." (the old menu is gone).
 - validate_source.py: the API-contract check now looks in SettingsActivity.kt; MainActivity.kt and activity_main.xml are removed from EMOJI_PENDING (left: Floater.kt H5, PluginStore.kt H2).
 - Version 0.9.21 (code 41).

Changed files: MainActivity.kt, activity_main.xml, SettingsActivity.kt (new), Updater.kt (1 text line), AndroidManifest.xml (SettingsActivity), q_styles.xml (ThemeOverlay.Q.Nav), new res: menu/q_nav_main.xml, color/q_nav_item.xml, drawable q_ic_apps/q_ic_settings/q_ic_globe, validate_source.py, build.gradle.kts.
Same ids kept: list, empty, count, empty_title, empty_msg, search, fab, filter_sort, app_menu. Removed ids: nav_dashboard, nav_installed, nav_import, nav_activity, nav_settings (nothing else referenced them).
Now unused (delete after grep -rn in the repo shows no hits): drawables bg_search, bg_chip, ic_add; colors bg/text/subtext/teal/accent may still be used by item_plugin.xml and other layouts.

Checked: validator passes on a copy with the real PluginActivity, PluginStore, BubbleService; all XML parses; no duplicate ids; every resource and id used by the new layout, menu, MainActivity and SettingsActivity exists (except the item_plugin ids, which live in the repo); brackets balanced; the three new icons were drawn and looked at.
Not built, not run on a device, layout not rendered (360/410/1280 px reviewed in code only).

Please check: build passes; Home and Plugins tabs; tap the search pill (keyboard opens); Back from Plugins; rotate on Plugins (stays on Plugins); Downloads and Browser tabs open and Back returns to the same tab; Settings (every row opens its dialog; bubble switch asks for the overlay permission and returns to OFF if refused); the active tab highlight colour; font size Large; TalkBack on the bar.
Send back: build errors, crash logs, screenshots of anything that looks wrong.
