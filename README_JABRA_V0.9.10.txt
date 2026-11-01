Jabra Extension Hub v0.9.10 - Browser page tools: find in page, text size, dark pages, save page

NEW (browser, 3-dot menu -> "Page tools"):
 - Find in page: a bar at the bottom with the search words, match count (3/12), up/down arrows and close. Back or the cross closes it. It closes when you switch tabs.
 - Text size: 80% to 200% for all pages and tabs (remembered). Default 100%.
 - Dark pages: turns light pages dark (colours inverted, images and video kept natural). On/off is remembered. Not applied to the new-tab page. Some sites may look odd; switch it off there.
 - Save page: "Save as PDF" opens Android's print dialog (choose Save as PDF). "Save as web archive (.mht)" saves one file to Downloads/Jabra Extension Hub (Android 10+) or opens the share sheet on older Android.
Changed: BrowserActivity.kt, BrowserStore.kt (text_zoom, dark_page), build.gradle.kts (0.9.10 / code 30).
Data: no change to bookmarks or history. Includes 0.9.9 (new-tab page, search engine, suggestions).
Not built or device-tested in this package.
Please check: menu -> Page tools opens and each entry works (if the submenu entries do nothing, tell me); find: type, next/prev, close; text size changes and survives restart; dark pages on a white site and a site with photos; save as PDF and as .mht.
