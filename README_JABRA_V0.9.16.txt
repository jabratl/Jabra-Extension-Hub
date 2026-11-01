Jabra Extension Hub v0.9.16 - Quiet Pill step D4: new-tab page restyle

NEW (new-tab page):
 - One big rounded search pill (search icon, no Go button, press Go on the keyboard). The engine name is in the placeholder; the old "change it in the menu" line is gone.
 - Bookmark tiles: 56 px rounded tile with the site's favicon (its own /favicon.ico). If it does not load, the tile keeps the first letter. 8 tiles shown, "Show all" reveals the rest (max 24).
 - Recent: only 4 pages, small favicon + title + site. Hidden when history is off or empty.
 - Colours = app palette (navy surfaces, orange accent) in dark; light palette when the phone/WebView reports light.

Changed: NewTabPage.kt (html() rewritten, helpers faviconOf/letterOf/iconHtml added; URL, isNewTab, response unchanged), build.gradle.kts (0.9.16 / code 36).
Not changed: BrowserActivity.kt, layouts, other files.
Checked: page script passes node --check, CSS/Kotlin brackets balanced. Not built, not rendered on a device.

Please check: page opens in a new tab and in a fresh browser start; typing a word searches, typing example.com opens it, Go key works; favicons show on bookmark tiles (white rounded square) and letters appear for sites without one; Show all works; Recent list; 360 px wide (4 tiles per row, 3 under 340 px); phone in light mode (page turns light while the app bars stay dark).
Trouble spots: (1) favicons blocked by the ad blocker or slow (letter stays, fine); (2) page light while app chrome is dark in light mode; (3) the pill sits close to the top on very short screens (10vh padding).
