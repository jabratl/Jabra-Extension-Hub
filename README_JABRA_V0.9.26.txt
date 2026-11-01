Jabra Extension Hub v0.9.26 - Quiet Pill step H5b: bubble and Quick input

CHANGED:
 - BubbleService.kt: the J bubble uses the app accent (q_accent, dark letter, dark ring) instead of hardcoded pink; notification icon is q_ic_add (was ic_add); the bubble has a spoken name ("Jabra bubble").
 - Bubble buttons are now pills: q_surface_hi with an outline, a round letter tile in the plugin's colour and the label, 48 dp high. The letter is the first letter of the button label (dark or white, chosen from the tile colour).
 - Floater.kt: no emoji left. FloaterEntry.icon is replaced by letter. The default tile colour is the app accent (was pink). A plugin's manifest "floater.icon" is ignored (an emoji there does nothing); "label", "color", "source", "allowEmpty" work as before. The built-in Novel Assistant Scan button shows the letter S.
 - SettingsActivity.kt, Quick input: after "Floating bubble" and "Scan copied text", one switch per installed plugin that offers a bubble button (same saved choice as the plugin menu's "Show in floating bubble"). If you switch one on while the bubble is off, a toast says to turn the bubble on.
 - validate_source.py: Floater.kt removed from EMOJI_PENDING (now empty), so the emoji check covers every source file.
 - Version 0.9.26 (code 46).

Changed files: BubbleService.kt, Floater.kt, SettingsActivity.kt, validate_source.py, build.gradle.kts (2 lines), README.
FloaterActivity.kt unchanged. drawable ic_add is now unused (delete after grep -rn shows no hits).
Not built, not run, not rendered.
Please check: bubble on, drag it, tap with 0, 1 and 2+ plugin buttons; tray opens left/right and up/down near screen edges; a plugin with a custom colour (light and dark) and a plugin without "color"; Settings > Quick input switches against the plugin menu; the foreground notification icon; TalkBack names on the bubble and buttons.
