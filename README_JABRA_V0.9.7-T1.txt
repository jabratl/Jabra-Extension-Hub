Jabra Extension Hub v0.9.7-t1 - Tabs (steps T1 + T2, browser part)

NEW in the Hub browser:
 - Tabs. A small "▢N" button (N = number of tabs) sits next to the menu button; the menu also has "Tabs (N)" and "New tab" at the top.
 - The Tabs list shows every tab (title, site). Tap to switch, ✕ to close, "New tab" to add one. Up to 8 tabs. Closing the last tab resets it to a fresh page.
 - Long-press a link (or a linked image): Open in new tab / Open in this tab / Copy link.
 - A real "new window" link (the page asks for a pop-up window after you tap) now opens as a new tab. Pop-ups you did not tap are still blocked.
 - Back: when a tab cannot go back any more and other tabs exist, it closes that tab and returns to the previous one; with one tab it leaves the browser as before.
 - Each tab keeps its own "found media" list and chip. Background tabs are paused. "Clear browsing data" clears the history of all tabs.
 - A link sent to a browser that is already open (intent extra "url") opens as a new tab (onNewIntent; BrowserActivity is now singleTop).
Changed: BrowserActivity.kt, AndroidManifest.xml (launchMode singleTop for BrowserActivity), build.gradle.kts (0.9.7-t1 / code 27).
Not changed: PluginActivity.kt, activity_browser.xml (not needed: the WebView is swapped inside its existing parent; the tab button is added in code).
Not built or device-tested in this package (no Kotlin compiler was available).
Please check: new tab, switch, close, last tab, back button, link long-press, a site that opens a pop-up window, a video in tab A then switch to tab B (sound should stop; the chip shows tab B's list), rotate, fullscreen video, Hub/plugin "open in browser".
Not yet: tabs are not restored after the app is closed (T3), no new-tab page (R1).
