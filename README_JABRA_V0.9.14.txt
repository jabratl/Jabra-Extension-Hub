Jabra Extension Hub v0.9.14 - Quiet Pill: bottom address pill + menu bottom sheet (steps D1 + D2)

NEW (browser):
 - The address bar is now a rounded pill at the BOTTOM. It shows a lock (or search icon) and only the site name. Tap it to type: it shows the full address, a clear button, and the tab/menu buttons step aside.
 - Suggestions open ABOVE the pill (own list, not the old popup).
 - Scroll down: the pill shrinks to a small capsule with only the site name. Scroll up (or load a page): it comes back. The page itself does not resize.
 - Loading progress is a thin rounded bar on the bottom edge of the pill.
 - Back button and X are gone: use the back gesture / back button of the phone. "Close browser" is at the bottom of the menu.
 - Tab button shows the number of open tabs (inside the pill).
 - The 3-dot menu is a bottom sheet: round quick buttons on top (Bookmark, Share, Find, Dark pages, Ad block, Desktop) and a short list below (no submenus). All old entries are still there (Page tools entries are in the list now).
 - Colours: the browser theme is dark only, so the design tokens use the app palette (same navy surfaces, accent orange #FF8A5B). The earlier #7C8CFF idea is not used.

Changed: BrowserActivity.kt, res/layout/activity_browser.xml (REPLACE your copy; ids back and close are removed), res/values/q_tokens.xml and res/values-night/q_tokens.xml (replace, new palette), q_pill.xml and q_sheet_bg.xml (replace), q_ic_lock_open.xml (new), build.gradle.kts (0.9.14 / code 34).
Not changed: Q.kt, other Kotlin files, item_media.xml, media panel, tab list dialog (next steps).
Copies of the layout/colour files seen so far are in docs/res_seen/ so you do not need to upload them again.
Not built or device-tested in this package.
Please check: pill at the bottom and not hidden by the keyboard; type an address and press Go; suggestions appear above the pill and tapping one opens it; scroll a long page (pill shrinks, comes back on scroll up); progress bar while loading; menu sheet opens, each entry works (Tabs, New tab, Bookmarks, History, Downloads, Forward, Reload, Copy link, Text size, Search engine, Save page, Ad blocker on this site, Desktop, Site settings, Save history, Export/import, Clear data, Close browser); quick buttons toggle; tab count changes; media chip still shows above the pill; Find in page bar still works (it covers the pill while open).
