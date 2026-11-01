Jabra Extension Hub v0.9.19 - H0: one colour palette, no emoji check

CHANGED:
 - Downloads screen and the browser background now use the q_* tokens (q_bg, q_surface, q_text, q_text_sub, q_accent, q_chip, q_chip_accent) instead of br_*. Colours are the same values; the card chip is now q_chip (surface_hi) and the accent chip is q_chip_accent.
 - Browser history dialog: the folder emoji is gone (folder name still shown after a dot).
 - Downloads: the "Play" button no longer has a triangle character in its text.
 - scripts/validate_source.py: new check, fails the build if an emoji or pictograph symbol appears in app/src/main/java or app/src/main/res (.kt/.xml). Files not cleaned yet are listed in EMOJI_PENDING with the step that cleans them: Floater.kt (H5), PluginStore.kt (H2), MainActivity.kt (H1/H2).
 - Version 0.9.19 (code 39).

Changed files: DownloadsActivity.kt, BrowserActivity.kt, activity_browser.xml, build.gradle.kts, scripts/validate_source.py, this file.

NOT done here (needs the main app files, no edit made):
 - br_* colour definitions (colors_browser.xml), br_chip / br_chip_accent drawables and bg_chip.xml are now unused by the files in this zip. Before deleting them, run: grep -rn "br_\|bg_chip" app/src  -- delete only when nothing is left (layouts such as item_plugin.xml may still use bg_chip).
 - Comment in q_tokens.xml still says "Same palette as br_*"; update when br_* is deleted.

Checked: the validator ran against the zip plus the attached main-app files; every edited .xml parses; no br_ reference is left in the files of this zip.
Not built, not run on a device.

Please check: build passes (also confirms the earlier ShapeAppearance.Q fix); validator step passes in CI; Downloads screen colours and the Play button; browser history dialog.
Send back: build errors, validator output, screenshots of anything that looks wrong.
