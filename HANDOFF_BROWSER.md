# Handoff: Jabra Hub browser
Type: MODIFY. Project: Jabra Hub browser. Base: jabra-hub v0.9.8 (zip). versionName must be plain x.y.z (the CI script scripts/validate_source.py rejects suffixes like -t1) and needs README_JABRA_V<version>.txt.
Done: B0, B1 (0.9.6), B2 manager, T0 refactor (build confirmed by user), T1 + T2 browser side. All of B2 + T0 + T1 + T2 ship as 0.9.7 (versionCode 27).

T1/T2 changes:
- BrowserActivity.kt: fields maxTabs=8, webHost/webIdx/webId/webLp (where the XML WebView sits; remembered in onCreate), tabBtn.
  newTab(url, show): Boolean (limit toast), switchTo(t) (pause + detach old, attach new at webIdx with webLp, same id, refresh urlBar/chip/panel), closeTab(t) (last tab resets), showTabs() (rows built in code), addTabButton() (inserts a TextView before R.id.menu only if its parent is a horizontal LinearLayout; colour copied from R.id.close if it is a TextView, else grey), showLinkMenu(), onNewIntent().
  setupTab: long-press listener (hitTestResult + requestFocusNodeHref), new-window tap uses newTab (falls back to same tab at the limit).
  goBack: closes the tab when it cannot go back and more tabs exist. Clear browsing data: clearHistory on all tabs. Menu: ids 20 Tabs, 21 New tab (orders -2, -1).
- AndroidManifest.xml: BrowserActivity launchMode singleTop.
Checks done: brackets balanced, manifest edit verified. NOT compiled, NOT run on a device.

USER MUST test (see README_JABRA_V0.9.7.txt). Likely trouble spots if something is off:
1. Tab button not visible / wrong colour: top bar is not a horizontal LinearLayout or bar colours differ. Menu "Tabs (N)" still works. Send activity_browser.xml to fix properly.
2. Sound or video keeps playing in a background tab: WebView.onPause may not stop media on this device; fix = also call pauseTimers / evaluate pause JS (planned in T3).
3. First WebView comes from XML, new ones are plain WebView(this): look/overscroll can differ slightly.
4. "Open in browser" from a plugin/Hub: onNewIntent only fires when BrowserActivity is the top activity (singleTop). If the browser is behind the plugin, a second browser instance opens. Fix needs PluginActivity.kt (where openBrowser starts the browser): add Intent.FLAG_ACTIVITY_REORDER_TO_FRONT (test on device that onNewIntent fires) or finish the plugin first. Please send PluginActivity.kt.
Next: T3 (restore tabs after restart: urls, max 8, last active; background tab pause + cap; media chip per tab is already per tab) then R1 (new-tab page with bookmarks grid, search engine choice, address suggestions), R2-R4, F.
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.7 (build confirmed: yes/no). Done: B0-B2, T0, T1, T2 (browser side). Next: T2 plugin side (send PluginActivity.kt) + T3.


## 0.9.8 (this zip)
BUG FIXED (my T1 mistake): PopupMenu.add(group,id,order,title) with a negative order throws IllegalArgumentException ("order does not contain a valid category"), so the 3-dot menu crashed. "Tabs"/"New tab" now use order 0 and are added first (menu keeps insertion order for equal orders). Rule for next time: menu orders must be >= 0.
T3 (partial): Tab.pending/savedTitle (lazy load on first show, loadPending()), BrowserStore.saveTabs/savedTabs/savedTabCur/clearTabs (JSON in browser_prefs, max 8, http/https only), saveTabs() in onPause (skipped and cleared when Save history is off), restore in onCreate (an "url" extra adds one extra tab), clear browsing data also clears saved tabs, media paused via JS when leaving a tab.
Not done from T3: memory cap beyond 8 tabs, per-tab restore of back/forward history.
Checks done: brackets balanced, no negative menu orders left. NOT compiled, NOT run on a device.
USER MUST test: menu opens; tab restore; Save history off; clear data; link long-press; tab button visible.
Still open: T2 plugin side (send PluginActivity.kt), tab button colour/position (send activity_browser.xml if wrong).
Next: R1 new-tab page + search engine + suggestions, then R2-R4, F.
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.8 (build: yes/no, validator: pass/fail). Done: B0-B2, T0-T3, T2 browser side. Next: T2 plugin side (PluginActivity.kt) + R1.


## 0.9.9 (this zip)
Done: T2 plugin side (user-confirmed working: PluginActivity.Bridge.openBrowser now adds FLAG_ACTIVITY_REORDER_TO_FRONT or FLAG_ACTIVITY_SINGLE_TOP; PluginActivity.kt is NOT in this zip, the user applied the patch in their own project - ask for the file again if it must be edited).
R1: NewTabPage.kt (new): https://jabra.newtab/ is answered by BrowserActivity's shouldInterceptRequest (HTML: search box, bookmark tiles, recent history), so back/forward/reload work. Not stored in history/last_url; address bar shows empty. BrowserStore: engines list (index in browser_prefs key "engine"), engine(), searchUrl(), suggest(). BrowserActivity: setUrlText(), showSuggest() (ListPopupWindow under the bar, non-modal), chooseEngine() (menu id 22, order 2), go() uses the chosen engine, all "https://duckduckgo.com" defaults replaced by NewTabPage.URL, tabHost() for the tab list.
Rules learned: menu orders must be >= 0. Layout activity_browser.xml was never seen; everything is built in code.
Checks done: brackets balanced, no leftover duckduckgo default URLs. NOT compiled, NOT run on a device.
USER MUST test: see README_JABRA_V0.9.9.txt. Likely trouble spots: (1) suggestion list width/position if the url bar parent is odd; (2) ListPopupWindow stealing keyboard focus (then set isFocusable false / use a PopupWindow); (3) new-tab page blank if shouldInterceptRequest is not hit for the https://jabra.newtab/ URL (check AdBlock/other interceptors); (4) back from a site opened from the new-tab page should return to the page.
Not done: favicons on tiles, per-engine suggestions from the web, editing tiles, "set as home page".
Next: R2 (find in page, text zoom, dark-page mode, save page), R3 (media panel: select all, filter, copy link, send all to Media Grabber via Floater.kt), R4 (ad-block per-site allow-list, per-site desktop mode, Downloads list after restart), F (360/410px, dark/light, build, device test, changelog).
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.9 (build: yes/no, validator: pass/fail). Done: B0-B2, T0-T3, T2 browser+plugin side, R1. Next: R2.
