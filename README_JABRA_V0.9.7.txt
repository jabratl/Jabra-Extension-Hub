Jabra Extension Hub v0.9.7 - Tabs, bookmark and history manager

NEW in the Hub browser:
 - Tabs. A small "▢N" button (N = number of tabs) sits next to the menu button; the menu also has "Tabs (N)" and "New tab" at the top.
   The Tabs list shows every tab (title, site): tap to switch, ✕ to close, "New tab" to add one. Up to 8 tabs. Closing the last tab resets it to a fresh page.
 - Long-press a link (or a linked image): Open in new tab / Open in this tab / Copy link.
 - A real "new window" link (the page opens a window after you tap) now opens as a new tab. Pop-ups you did not tap are still blocked.
 - Back: when a tab cannot go back any more and other tabs exist, it closes that tab and returns to the previous one; with one tab it leaves the browser as before.
 - Each tab keeps its own "found media" list and chip. Background tabs are paused. "Clear browsing data" clears the history of all tabs.
 - A link sent to a browser that is already open (intent extra "url") opens as a new tab (BrowserActivity is now singleTop).
 - Bookmarks and History open a manager with a search box (title, link or folder). Long-press: Open, Rename, Move to folder, Delete, Copy link (bookmarks);
   Open, Add bookmark, Delete from history, Copy link (history). History can be exported as a text file. "Clear" asks first.
Changed: BrowserActivity.kt (Tab class, setupTab, tabs, manager dialogs), BrowserStore.kt (rename / move / delete helpers, history export), AndroidManifest.xml (singleTop), build.gradle.kts (0.9.7 / code 27).
Data: no format change. Includes 0.9.6 (bookmark export / import / folders) and 0.9.5 (Share sheet "Jabra Grab").
Not built or device-tested in this package.
Not yet: tabs are not restored after the app is closed, no new-tab page, "open in browser" from a plugin opens a tab only when the browser is already the top screen.
