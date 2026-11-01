Jabra Extension Hub v0.9.42 - Step S17: found list and select bar on narrow screens

CHANGELOG 0.9.42 (code 62):
 S17
 - BrowserActivity.kt: the select bar (Done, "N selected", All/None, Copy, Send, Download) needs about 320dp, so on a 360dp phone the "N selected" count was squeezed out. Under 440dp wide, Download is now an accent icon button (48dp, description "Download selected"); from 440dp up it stays the chip with the word. Colours use the existing q_* tokens, no new colours.
 - build.gradle.kts: version 0.9.42 (code 62).
Not touched: colors.xml, values-night, layouts (item_media). The new code reads the width when the panel is drawn; after rotating, close and reopen the found list.
Checked: braces/parens balanced in BrowserActivity.kt.
Not built (no Kotlin compiler here), not run on a device; no screenshots at 360/410/1280 dark + light (no emulator here).
Please check: select mode on a 360dp and a 410dp phone: "12 selected" is readable, Download icon works and is orange; on a tablet/landscape the Download chip with the word shows; light and dark theme: the bar text and icons are readable.
