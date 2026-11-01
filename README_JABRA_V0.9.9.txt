Jabra Extension Hub v0.9.9 - New-tab page, search engine choice, address-bar suggestions

NEW (browser):
 - New tab page: search box, your bookmarks as tiles (up to 24) and the 6 latest history entries. "New tab" in the menu or the tab list opens it; so does a fresh start and closing the last tab. The address bar stays empty on it. It is never saved to history, bookmarks or "last page".
 - Search engine: 3-dot menu -> "Search engine: ..." (DuckDuckGo, Google, Bing, Brave, Startpage). Used by the address bar and the new-tab page. Default stays DuckDuckGo.
 - Address-bar suggestions: while you type, a list shows matching bookmarks (star) and history, with the first row "search for ...". Tap a row to open it.
 - Tapping the address bar selects all text.
FIXED / CHANGED:
 - Plugin "open in browser" (T2 plugin side, PluginActivity.kt): the browser is brought to the front and the link opens as a new tab, also when the browser is behind the plugin. (Confirmed working by the user on 0.9.8 + patch.)
Changed: NewTabPage.kt (new), BrowserActivity.kt, BrowserStore.kt, build.gradle.kts (0.9.9 / code 29). PluginActivity.kt: apply the openBrowser patch from the chat (FLAG_ACTIVITY_REORDER_TO_FRONT or FLAG_ACTIVITY_SINGLE_TOP) - the file is not in this zip.
Data: no change to bookmarks or history. New setting "engine" in browser_prefs.
Not built or device-tested in this package.
Please check: new tab opens the page; search from the page box and from the address bar; change the engine; type in the address bar (list appears, tap a row, Back closes the list); bookmark tiles open; restart keeps a new-tab tab; a bookmark/history item still works; 3-dot menu still opens.
