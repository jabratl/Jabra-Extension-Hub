Jabra Extension Hub v0.9.22 - Quiet Pill step H2: plugin library

CHANGED (main app):
 - Plugin card (item_plugin.xml) in the Quiet Pill style: q_* colours, 16 dp corners, 48 dp touch targets. The three-dot is now a vector icon button.
 - Plugin icon: a rounded tile with the first letter of the plugin name (accent colour, works for Chinese names too). If the manifest "icon" is a file inside the plugin (png, webp, jpg, up to 512 KB, no ".." in the path) that image is shown instead. An emoji or svg in "icon" is ignored and the letter tile is used. No emoji default any more (PluginStore.kt is clean).
 - Pin: a star button on every card (was "favourites" in the menu; same saved data). Wording is now "Pinned" everywhere (filter, sort, card menu: Pin to Home / Unpin from Home). No more star character in the plugin line.
 - Home: "Continue" card (the plugin you opened last; also set when a plugin is opened from the bubble, Share or Scan) and a "Pinned" grid (3 columns on phones, up to 6 on wide screens; long-press a tile for the plugin menu, same as long-press on a card).
 - PluginActivity: 2 lines remember the last opened plugin.
 - validate_source.py: PluginStore.kt removed from EMOJI_PENDING (left: Floater.kt, step H5).
 - Version 0.9.22 (code 42).

Changed files: MainActivity.kt, PluginStore.kt, PluginActivity.kt (2 lines), item_plugin.xml, activity_main.xml (Home sections), item_pin.xml (new), drawable/q_tile.xml (new), validate_source.py, build.gradle.kts.
Ids kept in item_plugin: icon, name, desc, meta, more. New: icon_img, pin.
Now unused (delete after grep -rn shows no hits): drawable bg_icon.

Checked: all XML parses, no duplicate ids, every resource used by the new layouts and MainActivity exists, brackets balanced, validator run on a copy with the real files.
Not built, not run, not rendered. Plugin image icons were not tested with a real plugin.

Please check: build passes; cards (tile letter, star toggles, three-dot menu, long-press); Home Continue after opening a plugin; pinned grid after pinning; a plugin with a png icon; a Chinese plugin name; font size Large; TalkBack on the star.
